package org.ut99android.host;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

public final class UnmodifiedPatchImportTest {
    public static void main(String[] args) throws Exception {
        Path archive = Path.of(args[0]), stage = Path.of(args[1]);
        byte[] before = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive));
        PatchArchiveProcessor.processUnmodified(archive, stage, null, new AtomicBoolean());
        int checked = 0;
        try (InputStream input = Files.newInputStream(archive);
             BZip2CompressorInputStream bz = new BZip2CompressorInputStream(input);
             TarArchiveInputStream tar = new TarArchiveInputStream(bz)) {
            TarArchiveEntry e;
            while ((e = tar.getNextEntry()) != null) {
                if (!e.isFile() || !e.getName().startsWith("SystemARM64/")) continue;
                Path original = stage.resolve("runtime").resolve(e.getName().substring("SystemARM64/".length()));
                if (!Files.isRegularFile(original)) continue;
                if (!Arrays.equals(tar.readAllBytes(), Files.readAllBytes(original))) throw new AssertionError("Changed " + e.getName());
                checked++;
            }
        }
        if (checked != 22) throw new AssertionError("Unexpected number of checked ARM64 files: " + checked);
        if (Files.exists(stage.resolve("runtime/ut-launch.so"))) throw new AssertionError("Transformed launcher present");
        if (!Files.exists(stage.resolve("game/System/Core.u"))) throw new AssertionError("Missing game patch content");
        if (!Arrays.equals(before, MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive)))) throw new AssertionError("Source archive changed");
        try {
            PatchArchiveProcessor.processUnmodified(archive, stage, null, new AtomicBoolean());
            throw new AssertionError("Existing stage overwritten");
        } catch (java.nio.file.FileAlreadyExistsException expected) { /* Preserve previous import. */ }
        System.out.println("Verified " + checked + " original ARM64 files and untouched source archive");
    }
}
