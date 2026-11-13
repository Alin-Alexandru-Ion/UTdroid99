package org.ut99android.host;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UnmodifiedPatchFailureTest {
    public static void main(String[] args) throws Exception {
        Path parent = Path.of(args[0]); Files.createDirectories(parent);
        Path invalid = parent.resolve("bad-archive"), stage = parent.resolve("must-not-exist");
        Files.write(invalid, new byte[]{1, 2, 3}, java.nio.file.StandardOpenOption.CREATE_NEW);
        try { PatchArchiveProcessor.processUnmodified(invalid, stage, null, new AtomicBoolean()); throw new AssertionError("Invalid archive accepted"); }
        catch (IOException expected) { if (Files.exists(stage)) throw new AssertionError("Invalid archive created stage"); }
        try { PatchArchiveProcessor.processUnmodified(Path.of(args[1]), stage, null, new AtomicBoolean(true)); throw new AssertionError("Cancellation ignored"); }
        catch (IOException expected) { if (Files.exists(stage)) throw new AssertionError("Cancellation created stage"); }
        System.out.println("Invalid and cancelled unmodified imports rejected without creating a stage");
    }
}
