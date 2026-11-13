package org.ut99android.host;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Copies an explicitly selected tree; never writes to the source provider. */
final class GameFolderImporter {
    interface Progress { void update(int files, long bytes, String path); }
    private static final int MAX_DEPTH = 48;
    private static final int MAX_FILES = 100000;
    private final ContentResolver resolver;
    private final Uri tree;
    private final AtomicBoolean cancelled;
    private final Progress progress;
    private final Set<String> visitedDirectories = new HashSet<>();
    private int files;
    private long bytes;

    private GameFolderImporter(Context context, Uri tree, AtomicBoolean cancelled, Progress progress) {
        this.resolver = context.getContentResolver(); this.tree = tree;
        this.cancelled = cancelled; this.progress = progress;
    }

    static File importedRoot(Context context) { return new File(context.getFilesDir(), "game-imports"); }
    static File active(Context context) {
        String id = context.getSharedPreferences("phone-storage", Context.MODE_PRIVATE).getString("import-id", "");
        return fromId(context, id);
    }
    static File fromId(Context context, String id) {
        if (id == null) return null;
        if (!id.matches("game-[a-f0-9-]{36}")) return null;
        File root = new File(importedRoot(context), id);
        return new File(root, ".ut99-import-complete").isFile() ? root : null;
    }
    static File copy(Context context, Uri tree, AtomicBoolean cancelled, Progress progress) throws IOException {
        File parent = importedRoot(context);
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create app storage.");
        File dest = new File(parent, "game-" + UUID.randomUUID());
        if (!dest.mkdir()) throw new IOException("Cannot create a new import folder.");
        boolean committed = false;
        try {
            GameFolderImporter importer = new GameFolderImporter(context, tree, cancelled, progress);
            importer.copyDirectory(DocumentsContract.getTreeDocumentId(tree), dest, "", 0);
            checkCancelled(cancelled);
            if (BuildConfig.ENGINE_FREE_EXPERIMENT) {
                progress.update(importer.files, importer.bytes, "Applying 469e patch files");
                PatchImportBridge.backend().mergeIntoGame(context, dest, cancelled);
            }
            String problem = StorageSetupActivity.problem(dest);
            if (problem != null) throw new IOException(problem);
            if (!new File(dest, ".ut99-import-complete").createNewFile()) throw new IOException("Cannot finish import.");
            checkCancelled(cancelled);
            if (!context.getSharedPreferences("phone-storage", Context.MODE_PRIVATE).edit()
                    .putString("import-id", dest.getName()).commit()) throw new IOException("Cannot save the selected game.");
            committed = true;
            return dest;
        } finally {
            // Only this invocation's newly created private directory is removed.
            if (!committed) deleteStaging(dest);
        }
    }
    private static void checkCancelled(AtomicBoolean cancel) throws IOException {
        if (cancel.get()) throw new IOException("Import cancelled.");
    }
    private static void deleteStaging(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteStaging(child);
        }
        file.delete();
    }
    private void copyDirectory(String docId, File dest, String path, int depth) throws IOException {
        checkCancelled(cancelled);
        if (depth > MAX_DEPTH || !visitedDirectories.add(docId)) throw new IOException("Folder tree is too deep or contains a cycle.");
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE};
        Set<String> names = new HashSet<>();
        try (Cursor cursor = resolver.query(children, columns, null, null, null)) {
            if (cursor == null) throw new IOException("The folder provider did not return its contents.");
            while (cursor.moveToNext()) {
                checkCancelled(cancelled);
                String childId = cursor.getString(0), name = cursor.getString(1), mime = cursor.getString(2);
                if (childId == null || name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                        || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0
                        || name.equals(".ut99-import-complete") || !names.add(name))
                    throw new IOException("Invalid or duplicate filename in " + path);
                File out = new File(dest, name);
                String relative = path.isEmpty() ? name : path + "/" + name;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (!out.mkdir()) throw new IOException("Cannot create " + relative);
                    copyDirectory(childId, out, relative, depth + 1);
                } else {
                    if (++files > MAX_FILES) throw new IOException("Too many files in selected folder.");
                    long expected = cursor.isNull(3) ? -1 : cursor.getLong(3);
                    if (expected >= 0 && expected > dest.getUsableSpace()) throw new IOException("Not enough free storage for " + relative);
                    progress.update(files, bytes, relative);
                    Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, childId);
                    long copied = 0, nextProgress = 8L * 1024 * 1024;
                    try (InputStream in = resolver.openInputStream(document); FileOutputStream output = new FileOutputStream(out)) {
                        if (in == null) throw new IOException("Cannot open " + relative);
                        byte[] buffer = new byte[65536]; int n;
                        while ((n = in.read(buffer)) != -1) {
                            checkCancelled(cancelled);
                            output.write(buffer, 0, n); copied += n; bytes += n;
                            if (copied >= nextProgress) { progress.update(files, bytes, relative); nextProgress = copied + 8L * 1024 * 1024; }
                        }
                    }
                    if (expected >= 0 && copied != expected) throw new IOException("Incomplete copy of " + relative);
                }
            }
        } catch (SecurityException e) { throw new IOException("Access to the selected folder was lost.", e); }
    }
}
