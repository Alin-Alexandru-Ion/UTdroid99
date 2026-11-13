package org.ut99android.host;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Private, staged patch import. The original provider is read-only. */
final class EngineFreePatchBackend implements PatchImportBridge.Backend {
    private static final long ARCHIVE_SIZE = 74481975L;
    private static final Set<String> SUPPORT = new HashSet<>(Arrays.asList(
        "libSDL2-2.0.so.0", "libc.so.6", "ld-linux-aarch64.so.1", "libpthread.so.0",
        "libutlibcprobe.so", "libdl.so.2", "libstdc++.so.6", "libm.so.6", "libgcc_s.so.1",
        "libopenal.so.1", "libxmp.so.4", "libsndfile.so.1", "libmpg123.so"));
    private static final long MAX_LIBRARY = 32L * 1024 * 1024;

    private static void cancelled(AtomicBoolean flag) throws IOException {
        if (flag.get()) throw new IOException("Import cancelled");
    }
    private static Path root(Context context) { return new File(context.getFilesDir(), "patch-imports").toPath(); }
    @Override public File active(Context context) {
        String id = context.getSharedPreferences("patch-storage", Context.MODE_PRIVATE).getString("patch-id", "");
        if (id == null || !id.matches("patch-[a-f0-9-]{36}")) return null;
        File dir = root(context).resolve(id).resolve("prepared").toFile();
        return new File(dir, ".patch-complete").isFile() ? dir : null;
    }

    @Override public void importPatch(Context context, Uri uri, AtomicBoolean flag, PatchImportBridge.Progress progress) throws IOException {
        Path parent = root(context); Files.createDirectories(parent);
        Path work = parent.resolve("patch-" + UUID.randomUUID()); Files.createDirectory(work);
        boolean committed = false;
        try {
            Path archive = work.resolve("selected.tar.bz2"); long copied = 0, next = 8L * 1024 * 1024;
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 OutputStream out = Files.newOutputStream(archive, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                if (in == null) throw new IOException("Could not open selected patch");
                byte[] buffer = new byte[65536]; int n;
                while ((n = in.read(buffer)) != -1) {
                    cancelled(flag); copied += n;
                    if (copied > ARCHIVE_SIZE) throw new IOException("Unexpected patch archive size");
                    out.write(buffer, 0, n);
                    if (copied >= next) { progress.update("Copying patch archive", copied, ARCHIVE_SIZE); next = copied + 8L * 1024 * 1024; }
                }
            }
            if (copied != ARCHIVE_SIZE) throw new IOException("Unexpected patch archive size");
            progress.update("Verifying and unpacking the patch. Please wait…");
            Path prepared = work.resolve("prepared");
            PatchArchiveProcessor.Progress extraction = new PatchArchiveProcessor.Progress() {
                @Override public void update(String name) { /* The byte-based callback is the visible progress. */ }
                @Override public void update(String name, long done, long total) { progress.update(name, done, total); }
            };
            if (BuildConfig.LINUX_RUNTIME_PROBE || BuildConfig.DIRECT_REHOST)
                PatchArchiveProcessor.processUnmodified(archive, prepared, extraction, flag);
            else
                PatchArchiveProcessor.process(archive, prepared, extraction, flag);
            cancelled(flag);
            Files.delete(archive); // This invocation's private copy, never the user's source.
            Files.createFile(prepared.resolve(".patch-complete"));
            if (!context.getSharedPreferences("patch-storage", Context.MODE_PRIVATE).edit()
                    .putString("patch-id", work.getFileName().toString()).commit()) throw new IOException("Could not save patch selection");
            committed = true;
        } finally { if (!committed) deleteOwnTree(work); }
    }

    private static void deleteOwnTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                if (e != null) throw e; Files.delete(dir); return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override public void mergeIntoGame(Context context, File game, AtomicBoolean flag) throws IOException {
        File selected = active(context);
        if (selected == null) throw new IOException("Import the 469e patch first");
        Path source = selected.toPath().resolve("game"), target = game.toPath();
        for (String directory : new String[]{"System", "Textures"}) {
            Path from = source.resolve(directory), to = target.resolve(directory);
            if (!Files.isDirectory(from)) throw new IOException("Patch content missing: " + directory);
            Files.createDirectories(to);
            try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(from)) {
                for (Path file : files) {
                    cancelled(flag);
                    if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) throw new IOException("Unexpected patch content");
                    Files.copy(file, to.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void stageInput(InputStream in, Path temp, String expectedHash) throws IOException {
        try (OutputStream out = Files.newOutputStream(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            if (!temp.toFile().setReadOnly()) throw new IOException("Cannot secure runtime file");
            byte[] buffer = new byte[65536]; int n; long total = 0;
            try {
                java.security.MessageDigest hash = java.security.MessageDigest.getInstance("SHA-256");
                while ((n = in.read(buffer)) != -1) {
                    total += n; if (total > MAX_LIBRARY) throw new IOException("Runtime file too large");
                    hash.update(buffer, 0, n); out.write(buffer, 0, n);
                }
                if (expectedHash != null) {
                    StringBuilder value = new StringBuilder();
                    for (byte b : hash.digest()) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
                    if (!expectedHash.contentEquals(value)) throw new IOException("Patch runtime file changed");
                }
            } catch (java.security.NoSuchAlgorithmException e) { throw new IOException("SHA-256 unavailable", e); }
        }
    }

    @Override public void installRuntime(Context context) throws IOException {
        if (BuildConfig.DIRECT_REHOST) { installDirectRuntime(context); return; }
        if (BuildConfig.LINUX_RUNTIME_PROBE) throw new IOException("Linux runtime is not integrated yet; this build only tests unchanged patch import");
        File selected = active(context);
        if (selected == null) throw new IOException("Import the 469e patch first");
        Path runtime = new File(context.getFilesDir(), "core-load").toPath(); Files.createDirectories(runtime);
        Map<String,String[]> catalog = new HashMap<>();
        for (String[] entry : RuntimeCatalog.FILES) catalog.put(entry[1], entry);
        Map<Path,Path> staged = new java.util.LinkedHashMap<>(); Set<String> seen = new HashSet<>();
        try {
            try (ZipInputStream zip = new ZipInputStream(context.getAssets().open("ut99-runtime.zip"))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (entry.isDirectory() || !SUPPORT.contains(name) || !seen.add(name)) throw new IOException("Unexpected support library");
                    Path temp = Files.createTempFile(runtime, "support-", ".tmp"); staged.put(temp, runtime.resolve(name));
                    stageInput(zip, temp, null); zip.closeEntry();
                }
            }
            if (!seen.equals(SUPPORT)) throw new IOException("Incomplete support libraries");
            Path engine = selected.toPath().resolve("runtime");
            try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(engine)) {
                for (Path source : files) {
                    String name = source.getFileName().toString(); String[] entry = catalog.get(name);
                    if (entry == null || SUPPORT.contains(name) || !Files.isRegularFile(source) || Files.isSymbolicLink(source))
                        throw new IOException("Unexpected imported runtime file: " + name);
                    Path temp = Files.createTempFile(runtime, "engine-", ".tmp"); staged.put(temp, runtime.resolve(name));
                    try (InputStream in = Files.newInputStream(source)) { stageInput(in, temp, entry[2]); }
                    seen.add(name);
                }
            }
            if (seen.size() != catalog.size()) throw new IOException("Incomplete imported runtime");
            for (Map.Entry<Path,Path> file : staged.entrySet()) Files.move(file.getKey(), file.getValue(), StandardCopyOption.REPLACE_EXISTING);
            for (String[] pair : new String[][]{{"Default.ini", "UnrealTournament.ini"}, {"DefUser.ini", "User.ini"}}) {
                Path target = runtime.resolve(pair[1]);
                if (!Files.exists(target)) Files.copy(runtime.resolve(pair[0]), target);
                if (!target.toFile().canWrite() && !target.toFile().setWritable(true, true)) throw new IOException("Cannot make settings writable");
            }
        } finally { for (Path temp : staged.keySet()) Files.deleteIfExists(temp); }
    }

    /** Stage original patch files unchanged. Compatibility images exist only in memory. */
    private void installDirectRuntime(Context context) throws IOException {
        File selected = active(context);
        if (selected == null) throw new IOException("Import the 469e patch first");
        Path runtime = new File(context.getFilesDir(), "core-load").toPath(); Files.createDirectories(runtime);
        Map<String,String> original = new HashMap<>();
        for (String[] row : RuntimeCatalog.FILES) if (!SUPPORT.contains(row[1])) original.put(row[1], row[2]);
        original.remove("ut-launch.so");
        original.put("ut-bin-arm64", "726b4fbeea173ce62e02a81f76638e939389e1be8b40fea88f0ac83b4d21e390");
        original.put("Render.so", "7b41c3849d9c0af8a9eb00b8a42063cf224677589e94819d47b40f03e6801181");
        original.put("IpDrv.so", "26a925876f45f080ece50988a9c07ada11a29f49897c6e412ed52562c2cdc170");
        original.put("ALAudio.so", "c8285102dac000c10168d21572d39b8a9a025c325a0d4339abcb8dc92bedf3be");
        Map<Path,Path> staged = new java.util.LinkedHashMap<>(); Set<String> seen = new HashSet<>();
        try {
            try (ZipInputStream zip = new ZipInputStream(context.getAssets().open("ut99-runtime.zip"))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (entry.isDirectory() || !SUPPORT.contains(name) || !seen.add(name)) throw new IOException("Unexpected support library");
                    Path temp = Files.createTempFile(runtime, "support-", ".tmp"); staged.put(temp, runtime.resolve(name));
                    stageInput(zip, temp, null); zip.closeEntry();
                }
            }
            if (!seen.equals(SUPPORT)) throw new IOException("Incomplete support libraries");
            Path sourceDir = selected.toPath().resolve("runtime");
            for (Map.Entry<String,String> item : original.entrySet()) {
                Path source = sourceDir.resolve(item.getKey());
                if (!Files.isRegularFile(source) || Files.isSymbolicLink(source)) throw new IOException("Original patch component missing: " + item.getKey());
                Path temp = Files.createTempFile(runtime, "original-", ".tmp"); staged.put(temp, runtime.resolve(item.getKey()));
                try (InputStream in = Files.newInputStream(source)) { stageInput(in, temp, item.getValue()); }
            }
            Path fontDir = runtime.resolve("Fonts"); Files.createDirectories(fontDir);
            for (String font : new String[]{"CourierPrime.ttf", "OpenSans-Regular.ttf", "Tinos-Regular.ttf"}) {
                Path source = sourceDir.resolve("Fonts").resolve(font);
                if (!Files.isRegularFile(source) || Files.isSymbolicLink(source)) throw new IOException("Patch font missing: " + font);
                Path temp = Files.createTempFile(runtime, "font-", ".tmp"); staged.put(temp, fontDir.resolve(font));
                try (InputStream in = Files.newInputStream(source)) { stageInput(in, temp, null); }
            }
            for (Map.Entry<Path,Path> file : staged.entrySet()) Files.move(file.getKey(), file.getValue(), StandardCopyOption.REPLACE_EXISTING);
            for (String[] pair : new String[][]{{"Default.ini", "UnrealTournament.ini"}, {"DefUser.ini", "User.ini"}}) {
                Path target = runtime.resolve(pair[1]);
                if (!Files.exists(target)) Files.copy(runtime.resolve(pair[0]), target);
                if (!target.toFile().canWrite() && !target.toFile().setWritable(true, true)) throw new IOException("Cannot make settings writable");
            }
        } finally { for (Path temp : staged.keySet()) Files.deleteIfExists(temp); }
    }
}
