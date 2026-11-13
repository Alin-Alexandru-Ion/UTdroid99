package org.ut99android.host;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Process;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** A live game may still be using a previous copy while setup is visible. */
final class GameSession {
    private static File marker(Context c) { return new File(c.getFilesDir(), "game-session.pid"); }
    static void start(Context c) {
        try { Files.write(marker(c).toPath(), Integer.toString(Process.myPid()).getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { android.util.Log.e("UT99Host", "Could not record game session", e); }
    }
    static void finish(Context c) {
        try {
            String value = new String(Files.readAllBytes(marker(c).toPath()), StandardCharsets.UTF_8).trim();
            if (value.equals(Integer.toString(Process.myPid()))) Files.write(marker(c).toPath(), "idle".getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }
    static boolean running(Context c) {
        try {
            String value = new String(Files.readAllBytes(marker(c).toPath()), StandardCharsets.UTF_8).trim();
            if (value.equals("idle")) return false;
            int pid = Integer.parseInt(value);
            ActivityManager manager = (ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
            java.util.List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
            if (processes == null) return true; // Cannot establish that deletion is safe.
            for (ActivityManager.RunningAppProcessInfo process : processes)
                if (process.pid == pid && process.processName.equals(c.getPackageName() + ":utexperiment")) return true;
        } catch (java.nio.file.NoSuchFileException ignored) {
            ActivityManager manager = (ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
            java.util.List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
            if (processes == null) return true;
            for (ActivityManager.RunningAppProcessInfo process : processes)
                if (process.processName.equals(c.getPackageName() + ":utexperiment")) return true;
            return false;
        }
        catch (Exception ignored) { return true; }
        return false;
    }
}
