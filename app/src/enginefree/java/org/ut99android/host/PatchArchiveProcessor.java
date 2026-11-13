package org.ut99android.host;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

/** Processes one pinned 469e archive into a new caller-owned staging directory. */
final class PatchArchiveProcessor {
    static final String PATCH_HASH = "4c3978073b12b049c3ffdeb4d275cfc7a2313650f3eb5b94db06fbfee77c3e3b";
    private static final Set<String> ENGINE = new HashSet<>(Arrays.asList(
        "ALAudio.so", "Core.so", "Editor.so", "Engine.so", "Fire.so", "IpDrv.so",
        "OpenGLDrv.so", "Render.so", "SDLDrv.so", "udemo.so", "UWeb.so",
        "VulkanDrv.so", "XOpenGLDrv.so", "ut-bin-arm64", "Default.ini", "DefUser.ini"));
    private static final Set<String> LINUX_LIBRARIES = new HashSet<>(Arrays.asList(
        "libmpg123.so", "libsndfile.so.1", "libxmp.so.4"));
    private static final long MAX_ENTRY = 64L * 1024 * 1024;
    private static final long MAX_EXTRACTED = 512L * 1024 * 1024;
    interface Progress {
        void update(String name);
        default void update(String name, long done, long total) { update(name); }
    }
    private PatchArchiveProcessor() {}

    private static void checkCancelled(AtomicBoolean cancelled) throws IOException {
        if (cancelled.get()) throw new IOException("Patch import cancelled");
    }

    private static String archiveHash(Path archive, AtomicBoolean cancelled) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(archive)) {
                byte[] buffer = new byte[65536]; int n;
                while ((n = in.read(buffer)) != -1) { checkCancelled(cancelled); digest.update(buffer, 0, n); }
            }
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException e) { throw new IOException("SHA-256 unavailable", e); }
    }

    private static String target(String name, boolean unchanged) throws IOException {
        if (name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0 || name.startsWith("/"))
            throw new IOException("Unsafe archive path");
        String[] parts = name.split("/", -1);
        for (String part : parts) if (part.equals(".") || part.equals("..") || part.isEmpty())
            throw new IOException("Unsafe archive path");
        if (parts.length == 2 && parts[0].equals("SystemARM64") &&
                (ENGINE.contains(parts[1]) || (unchanged && LINUX_LIBRARIES.contains(parts[1])))) {
            String dest = !unchanged && parts[1].equals("ut-bin-arm64") ? "ut-launch.so" : parts[1];
            return "runtime/" + dest;
        }
        if (unchanged && parts.length == 3 && parts[0].equals("SystemARM64") &&
                parts[1].equals("Fonts") && (parts[2].equals("CourierPrime.ttf") ||
                parts[2].equals("OpenSans-Regular.ttf") || parts[2].equals("Tinos-Regular.ttf")))
            return "runtime/Fonts/" + parts[2];
        if (parts.length == 2 && parts[0].equals("System") && parts[1].endsWith(".u"))
            return "game/System/" + parts[1];
        if (parts.length == 3 && parts[0].equals("SystemLocalized") && parts[1].equals("int") && parts[2].endsWith(".int"))
            return "game/System/" + parts[2];
        if (parts.length == 2 && name.equals("Textures/UWindowFonts.utx"))
            return "game/Textures/UWindowFonts.utx";
        return null;
    }

    private static byte[] readEntry(TarArchiveInputStream tar, long size, AtomicBoolean cancelled) throws IOException {
        if (size < 0 || size > MAX_ENTRY) throw new IOException("Patch entry is too large");
        ByteArrayOutputStream out = new ByteArrayOutputStream((int)size);
        byte[] buffer = new byte[65536]; long remaining = size;
        while (remaining > 0) {
            checkCancelled(cancelled);
            int count = tar.read(buffer, 0, (int)Math.min(buffer.length, remaining));
            if (count <= 0) throw new IOException("Incomplete patch entry");
            out.write(buffer, 0, count); remaining -= count;
        }
        return out.toByteArray();
    }

    private static void copyEntry(TarArchiveInputStream tar, long size, Path output, AtomicBoolean cancelled) throws IOException {
        byte[] buffer = new byte[65536]; long remaining = size;
        try (java.io.OutputStream out = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            while (remaining > 0) {
                checkCancelled(cancelled);
                int count = tar.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                if (count <= 0) throw new IOException("Incomplete patch entry");
                out.write(buffer, 0, count); remaining -= count;
            }
        }
    }

    static void process(Path archive, Path newStage, Progress progress) throws IOException {
        process(archive, newStage, progress, new AtomicBoolean());
    }

    static void process(Path archive, Path newStage, Progress progress, AtomicBoolean cancelled) throws IOException {
        processInternal(archive, newStage, progress, cancelled, false);
    }

    /** Prepare original Linux files for a future Linux runtime; never transforms engine bytes. */
    static void processUnmodified(Path archive, Path newStage, Progress progress, AtomicBoolean cancelled) throws IOException {
        processInternal(archive, newStage, progress, cancelled, true);
    }

    private static void processInternal(Path archive, Path newStage, Progress progress, AtomicBoolean cancelled,
                                        boolean unchanged) throws IOException {
        if (!archiveHash(archive, cancelled).equals(PATCH_HASH)) throw new IOException("Select the official 469e Linux ARM64 patch archive");
        checkCancelled(cancelled);
        Files.createDirectory(newStage); // Never replace an existing import or its settings.
        Map<String, String> expected = new HashMap<>();
        for (String[] row : RuntimeCatalog.FILES) expected.put(row[1], row[2]);
        Set<String> names = new HashSet<>(), found = new HashSet<>(); long bytes = 0; int entries = 0;
        final long archiveBytes = Files.size(archive);
        try (InputStream raw = Files.newInputStream(archive);
             InputStream measured = new java.io.FilterInputStream(raw) {
                 private long count, next = 512 * 1024;
                 @Override public int read() throws IOException { int value = super.read(); if (value >= 0) advanced(1); return value; }
                 @Override public int read(byte[] b, int off, int len) throws IOException { int n = in.read(b, off, len); if (n > 0) advanced(n); return n; }
                 private void advanced(int n) { count += n; if (count >= next) { if (progress != null) progress.update("Unpacking patch. Please wait…", Math.min(count, archiveBytes), archiveBytes); next = count + 512 * 1024; } }
             };
             BZip2CompressorInputStream bzip = new BZip2CompressorInputStream(measured);
             TarArchiveInputStream tar = new TarArchiveInputStream(bzip)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                checkCancelled(cancelled);
                if (++entries > 10000) throw new IOException("Too many patch entries");
                if (entry.isDirectory()) continue;
                if (!entry.isFile()) continue;
                String dest = target(entry.getName(), unchanged);
                if (dest == null) continue;
                if (!names.add(dest.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate patch entry");
                long size = entry.getSize();
                if (size < 0 || size > MAX_ENTRY || bytes > MAX_EXTRACTED - size) throw new IOException("Patch exceeds storage limit");
                bytes += size;
                if (progress != null) progress.update(dest);
                Path output = newStage.resolve(dest);
                Files.createDirectories(output.getParent());
                if (dest.startsWith("game/")) {
                    copyEntry(tar, size, output, cancelled);
                    continue;
                }
                if (unchanged) {
                    copyEntry(tar, size, output, cancelled);
                    found.add(dest.substring("runtime/".length()));
                    continue;
                }
                byte[] data = readEntry(tar, size, cancelled);
                if (dest.equals("runtime/ut-launch.so")) data = PatchElfTransformer.launcher(data);
                else if (dest.equals("runtime/Render.so")) data = PatchElfTransformer.makeSymbolsGlobal(data, "7b41c3849d9c0af8a9eb00b8a42063cf224677589e94819d47b40f03e6801181", 4);
                else if (dest.equals("runtime/IpDrv.so")) data = PatchElfTransformer.makeSymbolsGlobal(data, "26a925876f45f080ece50988a9c07ada11a29f49897c6e412ed52562c2cdc170", 1);
                else if (dest.equals("runtime/ALAudio.so")) data = PatchElfTransformer.makeSymbolsGlobal(data, "c8285102dac000c10168d21572d39b8a9a025c325a0d4339abcb8dc92bedf3be", 1);
                if (dest.startsWith("runtime/")) {
                    String name = dest.substring("runtime/".length());
                    if (!expected.containsKey(name) || !PatchElfTransformer.sha256(data).equals(expected.get(name)))
                        throw new IOException("Unexpected processed patch component: " + name);
                    found.add(name);
                }
                Files.write(output, data, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }
        }
        Set<String> expectedEngine = new HashSet<>(ENGINE);
        if (unchanged) {
            expectedEngine.addAll(LINUX_LIBRARIES);
            expectedEngine.addAll(Arrays.asList("Fonts/CourierPrime.ttf", "Fonts/OpenSans-Regular.ttf", "Fonts/Tinos-Regular.ttf"));
        } else {
            expectedEngine.remove("ut-bin-arm64"); expectedEngine.add("ut-launch.so");
        }
        if (!found.equals(expectedEngine) || !Files.isRegularFile(newStage.resolve("game/System/Core.u"))
                || !Files.isRegularFile(newStage.resolve("game/System/Core.int"))
                || !Files.isRegularFile(newStage.resolve("game/Textures/UWindowFonts.utx")))
            throw new IOException("The patch is missing required files");
    }
}
