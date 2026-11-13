package org.ut99android.host;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipFile;

/** Exercises the real user-provided patch in a new isolated staging directory. */
public final class PatchArchiveProcessorTest {
    public static void main(String[] args) throws Exception {
        Path archive = Path.of(args[0]), stage = Path.of(args[1]);
        byte[] originalHash = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive));
        PatchArchiveProcessor.process(archive, stage, null);
        try (ZipFile reference = new ZipFile(args[2])) {
            String[] names = {"ut-launch.so", "Core.so", "Engine.so", "Render.so", "IpDrv.so", "ALAudio.so", "SDLDrv.so"};
            for (String name : names) {
                byte[] actual = Files.readAllBytes(stage.resolve("runtime").resolve(name));
                byte[] expected = reference.getInputStream(reference.getEntry(name)).readAllBytes();
                if (!Arrays.equals(actual, expected)) throw new AssertionError(name + " differs from tested build");
            }
        }
        for (String name : new String[]{"System/Core.u", "System/Core.int", "Textures/UWindowFonts.utx"})
            if (Files.size(stage.resolve("game").resolve(name)) == 0) throw new AssertionError("Missing " + name);
        if (!Arrays.equals(originalHash, java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))))
            throw new AssertionError("Patch archive changed");
        try { PatchArchiveProcessor.process(archive, stage, null); throw new AssertionError("Existing stage overwritten"); }
        catch (IOException expected) { System.out.println("Existing staging directory preserved"); }
        System.out.println("Patch processed; selected engine files match; game packages extracted; source unchanged");
    }
}
