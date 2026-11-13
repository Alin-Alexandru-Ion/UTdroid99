package org.ut99android.host;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Manages app-owned copies and imports only individually selected content. */
final class GameContentManager {
    static final String[] FOLDERS = {"Maps", "Textures", "Sounds", "Music", "System"};
    static final String[] DESCRIPTIONS = {"Maps (.unr)", "Textures (.utx)", "Sounds (.uax)", "Music (.umx)", "Scripts and metadata (.u, .int)"};
    interface Progress { void update(String name, int done, int total); }
    static final class Copy {
        final String id; final long time; final long bytes; final boolean active;
        Copy(String id, long time, long bytes, boolean active) { this.id=id; this.time=time; this.bytes=bytes; this.active=active; }
    }
    static final class Result { int added, skipped; }
    private static final class Selected { final String name; final Uri uri; final long size; Path staged;
        Selected(String name, Uri uri, long size) { this.name=name; this.uri=uri; this.size=size; } }

    private static void requireStopped(Context c) throws IOException {
        if (GameSession.running(c)) throw new IOException("Exit the game before managing copies or adding files.");
    }
    private static File checkedCopy(Context c, String id) throws IOException {
        File root = GameFolderImporter.importedRoot(c).getCanonicalFile();
        File copy = GameFolderImporter.fromId(c, id);
        if (copy == null || Files.isSymbolicLink(copy.toPath()) || !root.equals(copy.getCanonicalFile().getParentFile()))
            throw new IOException("The game copy is no longer available.");
        return copy;
    }
    static List<Copy> copies(Context c) throws IOException {
        File root = GameFolderImporter.importedRoot(c); File[] entries = root.listFiles();
        List<Copy> result = new ArrayList<>(); if (entries == null) return result;
        File active = GameFolderImporter.active(c);
        for (File item : entries) {
            File valid = GameFolderImporter.fromId(c, item.getName());
            if (valid == null || Files.isSymbolicLink(item.toPath()) || !root.getCanonicalFile().equals(item.getCanonicalFile().getParentFile())) continue;
            final long[] size = {0};
            Files.walkFileTree(valid.toPath(), new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) { size[0] += a.size(); return FileVisitResult.CONTINUE; }
            });
            result.add(new Copy(item.getName(), new File(item, ".ut99-import-complete").lastModified(), size[0], active != null && active.equals(item)));
        }
        result.sort((a,b) -> Long.compare(b.time, a.time));
        return result;
    }
    static void activate(Context c, String id) throws IOException {
        requireStopped(c); File copy = checkedCopy(c, id); String problem = StorageSetupActivity.problem(copy);
        if (problem != null) throw new IOException(problem);
        if (!c.getSharedPreferences("phone-storage", Context.MODE_PRIVATE).edit().putString("import-id", id).commit())
            throw new IOException("Could not select that game copy.");
    }
    static void delete(Context c, String id) throws IOException {
        requireStopped(c); File copy = checkedCopy(c, id); File active = GameFolderImporter.active(c);
        if (active != null && active.getName().equals(id)) throw new IOException("The active game copy cannot be deleted.");
        Files.walkFileTree(copy.toPath(), new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException { Files.delete(f); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path d, IOException error) throws IOException {
                if (error != null) throw error; Files.delete(d); return FileVisitResult.CONTINUE;
            }
        });
    }
    private static Selected metadata(Context c, Uri uri) throws IOException {
        try (Cursor cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (cur == null || !cur.moveToFirst()) throw new IOException("Could not read a selected filename.");
            String n=cur.getString(0);
            if (n == null || n.isEmpty() || n.equals(".") || n.equals("..") || n.indexOf('/')>=0 || n.indexOf('\\')>=0 || n.indexOf('\0')>=0)
                throw new IOException("A selected filename is invalid.");
            return new Selected(n, uri, cur.isNull(1) ? -1 : cur.getLong(1));
        } catch (SecurityException e) { throw new IOException("Access to a selected file was lost.", e); }
    }
    private static boolean allowed(int category, String name) {
        String n=name.toLowerCase(Locale.ROOT);
        switch(category) {
            case 0: return n.endsWith(".unr");
            case 1: return n.endsWith(".utx");
            case 2: return n.endsWith(".uax");
            case 3: return n.endsWith(".umx");
            case 4: return n.endsWith(".u") || n.endsWith(".int");
            default: return false;
        }
    }
    static Result add(Context c, String id, int category, List<Uri> uris, AtomicBoolean cancel, Progress progress) throws IOException {
        requireStopped(c); File copy=checkedCopy(c,id); File active=GameFolderImporter.active(c);
        if (active == null || !active.getName().equals(id)) throw new IOException("The active game copy changed.");
        if (category < 0 || category >= FOLDERS.length || uris.isEmpty() || uris.size() > 512) throw new IOException("Select between 1 and 512 files.");
        File target=new File(copy,FOLDERS[category]);
        if (!target.isDirectory() || Files.isSymbolicLink(target.toPath()) || !copy.getCanonicalFile().equals(target.getCanonicalFile().getParentFile()))
            throw new IOException("The destination folder is unavailable.");
        Set<String> existing=new HashSet<>(), selectedNames=new HashSet<>();
        String[] names=target.list(); if (names==null) throw new IOException("Cannot read the destination folder.");
        for (String name:names) existing.add(name.toLowerCase(Locale.ROOT));
        List<Selected> selected=new ArrayList<>(); Result result=new Result();
        for (Uri uri:uris) {
            Selected entry=metadata(c,uri); String name=entry.name; String lower=name.toLowerCase(Locale.ROOT);
            if (!allowed(category,name)) throw new IOException(name + " is not supported in " + FOLDERS[category] + ".");
            if (!selectedNames.add(lower)) throw new IOException("The selection contains duplicate names: " + name);
            if (existing.contains(lower)) { result.skipped++; continue; }
            selected.add(entry);
        }
        List<Path> stages=new ArrayList<>(), added=new ArrayList<>();
        try {
            int count=0;
            for (Selected file:selected) {
                if (cancel.get()) throw new IOException("Import cancelled.");
                progress.update(file.name, ++count, selected.size());
                file.staged=Files.createTempFile(target.toPath(), ".utdroid99-add-", ".tmp"); stages.add(file.staged);
                try (InputStream in=c.getContentResolver().openInputStream(file.uri); FileOutputStream out=new FileOutputStream(file.staged.toFile())) {
                    if (in==null) throw new IOException("Could not open " + file.name);
                    byte[] buffer=new byte[65536]; int n;
                    while ((n=in.read(buffer))!=-1) { if(cancel.get()) throw new IOException("Import cancelled."); out.write(buffer,0,n); }
                    out.getFD().sync();
                } catch (SecurityException e) { throw new IOException("Access to " + file.name + " was lost.",e); }
                long copied=Files.size(file.staged);
                if (copied==0) throw new IOException(file.name + " is empty.");
                if (file.size>=0 && copied!=file.size) throw new IOException(file.name + " was not copied completely.");
            }
            requireStopped(c); if (cancel.get()) throw new IOException("Import cancelled.");
            File current=GameFolderImporter.active(c);
            if (current==null || !current.getName().equals(id)) throw new IOException("The active game copy changed.");
            for (Selected file:selected) {
                Path destination=new File(target,file.name).toPath();
                // An exclusive create avoids overwriting a file that appeared while copying.
                Files.createFile(destination); added.add(destination);
                Files.move(file.staged,destination,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                result.added++;
            }
            return result;
        } catch (IOException | RuntimeException e) {
            for (Path file:added) try { Files.deleteIfExists(file); } catch(IOException ignored) { }
            throw e;
        } finally { for (Path file:stages) try { Files.deleteIfExists(file); } catch(IOException ignored) { } }
    }
}
