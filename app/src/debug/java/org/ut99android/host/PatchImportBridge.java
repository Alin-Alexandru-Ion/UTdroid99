package org.ut99android.host;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps the optional archive implementation out of the existing local-build variant. */
final class PatchImportBridge {
    interface Progress {
        void update(String text);
        default void update(String text, long done, long total) { update(text); }
    }
    interface Backend {
        File active(Context context);
        void importPatch(Context context, Uri uri, AtomicBoolean cancelled, Progress progress) throws IOException;
        void mergeIntoGame(Context context, File game, AtomicBoolean cancelled) throws IOException;
        void installRuntime(Context context) throws IOException;
    }
    private PatchImportBridge() {}
    static Backend backend() throws IOException {
        try {
            return (Backend)Class.forName("org.ut99android.host.EngineFreePatchBackend").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IOException("Patch importer unavailable in this build", e);
        }
    }
}
