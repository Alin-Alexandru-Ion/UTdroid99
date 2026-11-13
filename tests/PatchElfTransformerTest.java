package org.ut99android.host;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipFile;

/** Compares Java output with the previously built private runtime. */
public final class PatchElfTransformerTest {
    private static void same(ZipFile expected, String name, byte[] actual) throws Exception {
        byte[] reference = expected.getInputStream(expected.getEntry(name)).readAllBytes();
        if (!Arrays.equals(reference, actual)) throw new AssertionError(name + " output differs");
        System.out.println(name + ": byte-for-byte match");
    }
    public static void main(String[] args) throws Exception {
        Path system = Path.of(args[0]);
        try (ZipFile runtime = new ZipFile(args[1])) {
            byte[] source = Files.readAllBytes(system.resolve("ut-bin-arm64"));
            byte[] original = source.clone();
            same(runtime, "ut-launch.so", PatchElfTransformer.launcher(source));
            if (!Arrays.equals(original, source)) throw new AssertionError("Input was modified");
            same(runtime, "Render.so", PatchElfTransformer.makeSymbolsGlobal(
                    Files.readAllBytes(system.resolve("Render.so")),
                    "7b41c3849d9c0af8a9eb00b8a42063cf224677589e94819d47b40f03e6801181", 4));
            same(runtime, "IpDrv.so", PatchElfTransformer.makeSymbolsGlobal(
                    Files.readAllBytes(system.resolve("IpDrv.so")),
                    "26a925876f45f080ece50988a9c07ada11a29f49897c6e412ed52562c2cdc170", 1));
            same(runtime, "ALAudio.so", PatchElfTransformer.makeSymbolsGlobal(
                    Files.readAllBytes(system.resolve("ALAudio.so")),
                    "c8285102dac000c10168d21572d39b8a9a025c325a0d4339abcb8dc92bedf3be", 1));
            source[0] ^= 1;
            try { PatchElfTransformer.launcher(source); throw new AssertionError("Invalid input accepted"); }
            catch (IOException expected) { System.out.println("Invalid input rejected"); }
        }
    }
}
