package org.ut99android.host;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PatchArchiveFailureTest {
    public static void main(String[] args) throws Exception {
        Path parent = Path.of(args[0]); Files.createDirectories(parent);
        Path input = parent.resolve("invalid-patch.dat"), stage = parent.resolve("must-not-exist");
        Files.write(input, new byte[]{1, 2, 3}, java.nio.file.StandardOpenOption.CREATE_NEW);
        try { PatchArchiveProcessor.process(input, stage, null); throw new AssertionError("Invalid patch accepted"); }
        catch (IOException expected) { if (Files.exists(stage)) throw new AssertionError("Invalid patch created staging"); }
        try { PatchArchiveProcessor.process(input, stage, null, new AtomicBoolean(true)); throw new AssertionError("Cancellation ignored"); }
        catch (IOException expected) { if (Files.exists(stage)) throw new AssertionError("Cancelled patch created staging"); }
        if (Files.size(input) != 3) throw new AssertionError("Original input changed");
        System.out.println("Invalid and cancelled inputs rejected without creating staging or changing source");
    }
}
