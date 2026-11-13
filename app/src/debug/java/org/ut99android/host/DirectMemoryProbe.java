package org.ut99android.host;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Experimental pinned launcher adaptation, performed only on transient bytes. */
public final class DirectMemoryProbe {
    private DirectMemoryProbe() { }
    public static void registerAdaptations(String directory) throws Exception {
        Path dir = Paths.get(directory, "core-load");
        register("ut-launch.so", PatchElfTransformer.launcher(Files.readAllBytes(dir.resolve("ut-bin-arm64"))));
        register("Render.so", PatchElfTransformer.makeSymbolsGlobal(Files.readAllBytes(dir.resolve("Render.so")), "7b41c3849d9c0af8a9eb00b8a42063cf224677589e94819d47b40f03e6801181", 4));
        register("IpDrv.so", PatchElfTransformer.makeSymbolsGlobal(Files.readAllBytes(dir.resolve("IpDrv.so")), "26a925876f45f080ece50988a9c07ada11a29f49897c6e412ed52562c2cdc170", 1));
        register("ALAudio.so", PatchElfTransformer.makeSymbolsGlobal(Files.readAllBytes(dir.resolve("ALAudio.so")), "c8285102dac000c10168d21572d39b8a9a025c325a0d4339abcb8dc92bedf3be", 1));
    }
    private static void register(String name, byte[] image) throws java.io.IOException {
        String error = MainActivity.registerAdaptedImage(name, image);
        if (error != null) throw new java.io.IOException(name + ": " + error);
    }
    public static String runRegistered(String directory) {
        try { registerAdaptations(directory); return MainActivity.probeRegisteredModules(directory); }
        catch (Exception e) { return "Registered probe failed: " + e; }
    }
    public static String run(String directory) {
        try {
            Path path = Paths.get(directory, "core-load", "ut-bin-arm64");
            byte[] original = Files.readAllBytes(path);
            String before = PatchElfTransformer.sha256(original);
            byte[] adapted = PatchElfTransformer.launcher(original);
            String result = MainActivity.probeDirectMemory(directory, adapted);
            String after = PatchElfTransformer.sha256(Files.readAllBytes(path));
            return result + "; original file " + (before.equals(after) ? "unchanged" : "CHANGED");
        } catch (Exception e) { return "Direct memory probe failed: " + e; }
    }
}
