package org.ut99android.host;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Imports only the catalogued experimental runtime, never arbitrary shared code. */
final class SharedRuntimeInstaller {
    private static final long MAX_BYTES = 32L * 1024 * 1024;
    private SharedRuntimeInstaller() {}

    static void install(Context context) throws IOException {
        File dir = new File(context.getFilesDir(), "core-load");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create private runtime folder.");
        List<Path> staged = new ArrayList<>();
        List<Path> targets = new ArrayList<>();
        Map<String, String[]> expected = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (String[] entry : RuntimeCatalog.FILES) expected.put(entry[0], entry);
        try {
            try (ZipInputStream input = new ZipInputStream(context.getAssets().open("ut99-runtime.zip"))) {
              ZipEntry zipEntry;
              while ((zipEntry = input.getNextEntry()) != null) {
                String[] entry = expected.get(zipEntry.getName());
                if (entry == null || zipEntry.isDirectory() || !seen.add(entry[0]))
                    throw new IOException("Unexpected runtime archive entry: " + zipEntry.getName());
                Path target = new File(dir, entry[1]).toPath();
                Path temp = Files.createTempFile(dir.toPath(), "runtime-import-", ".tmp");
                staged.add(temp);
                targets.add(target);
                MessageDigest hash;
                try { hash = MessageDigest.getInstance("SHA-256"); }
                catch (NoSuchAlgorithmException e) { throw new IOException("SHA-256 unavailable", e); }
                long count = 0;
                try (FileOutputStream output = new FileOutputStream(temp.toFile())) {
                    if (!temp.toFile().setReadOnly()) throw new IOException("Cannot secure " + entry[0]);
                    byte[] buffer = new byte[65536];
                    int n;
                    while ((n = input.read(buffer)) != -1) {
                        count += n;
                        if (count > MAX_BYTES) throw new IOException("Runtime file is too large: " + entry[0]);
                        hash.update(buffer, 0, n);
                        output.write(buffer, 0, n);
                    }
                    output.getFD().sync();
                }
                StringBuilder actual = new StringBuilder();
                for (byte b : hash.digest()) actual.append(String.format(Locale.ROOT, "%02x", b & 0xff));
                if (!entry[2].contentEquals(actual)) throw new IOException("Wrong runtime file: " + entry[0]);
                input.closeEntry();
              }
            }
            if (seen.size() != expected.size()) throw new IOException("The included runtime is incomplete.");
            // Verify the whole set before replacing any previously staged file.
            for (int i = 0; i < staged.size(); i++)
                Files.move(staged.get(i), targets.get(i), StandardCopyOption.REPLACE_EXISTING);
            createConfigIfMissing(dir, "Default.ini", "UnrealTournament.ini");
            createConfigIfMissing(dir, "DefUser.ini", "User.ini");
        } finally {
            for (Path temp : staged) Files.deleteIfExists(temp);
        }
    }

    private static void createConfigIfMissing(File dir, String template, String name) throws IOException {
        Path target = new File(dir, name).toPath();
        if (!Files.exists(target)) {
            Files.copy(new File(dir, template).toPath(), target);
        }
        if (!target.toFile().canWrite() && !target.toFile().setWritable(true, true))
            throw new IOException("Cannot make " + name + " writable.");
    }
}
