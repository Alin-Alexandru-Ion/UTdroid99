package org.ut99android.host;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Best-effort reporting for the isolated experimental game process. */
public final class CrashReporter {
    private static final String TAG = "UT99Host";
    private static final Object LOCK = new Object();
    private static final int MAX_LOG = 128 * 1024;
    private static final int MAX_TRACE = 4 * 1024 * 1024;
    private CrashReporter() {}

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("crash-report", Context.MODE_PRIVATE); }
    private static File privateFile(Context c, String path) { return new File(c.getFilesDir(), path); }
    static File reports(Context c) { return new File(c.getFilesDir(), "Reports"); }

    static void markLaunch(Context context) {
        synchronized (LOCK) {
            capturePending(context);
            privateFile(context, "game-java-crash.txt").delete();
            privateFile(context, "core-load/runtime-status.txt").delete();
            prefs(context).edit().putLong("launch", System.currentTimeMillis()).commit();
        }
    }

    static void installGameExceptionHandler(Context context) {
        Context app = context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            recordGameFailure(app, "Uncaught exception on thread " + thread.getName(), error);
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    static void recordGameFailure(Context context, String stage, Throwable error) {
        try (PrintWriter out = new PrintWriter(privateFile(context, "game-java-crash.txt"), "UTF-8")) {
            out.println(stage);
            error.printStackTrace(out);
        } catch (Exception ignored) { }
    }

    public static void check(Context context) {
        Context app = context.getApplicationContext();
        new Thread(() -> { synchronized (LOCK) { capturePending(app); } }, "ut99-crash-report").start();
    }

    static void writeSetupFailure(Context context, Exception error) {
        synchronized (LOCK) {
            try {
                StringWriter details = new StringWriter();
                error.printStackTrace(new PrintWriter(details));
                writeReport(context, "Runtime preparation failed\n\n" + details, null, 0);
            } catch (Exception e) { Log.e(TAG, "Could not save setup report", e); }
        }
    }

    static String writeManual(Context context, String setupStatus) throws IOException {
        synchronized (LOCK) {
            StringBuilder body = new StringBuilder("Manual diagnostic snapshot\n");
            body.append("Setup: ").append(setupStatus).append('\n');
            long launch = prefs(context).getLong("launch", 0);
            body.append("Last launch: ").append(launch == 0 ? "not recorded" : new Date(launch)).append('\n');
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ApplicationExitInfo selected = null;
            for (ApplicationExitInfo info : manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, 32)) {
                if ((context.getPackageName() + ":utexperiment").equals(info.getProcessName())
                        && (selected == null || info.getTimestamp() > selected.getTimestamp())) selected = info;
            }
            if (selected != null) {
                body.append("Last recorded game-process exit: ").append(new Date(selected.getTimestamp()))
                        .append("; reason ").append(reason(selected.getReason())).append(" (").append(selected.getReason())
                        .append("); status ").append(selected.getStatus()).append("; PID ").append(selected.getPid()).append('\n');
            }
            addLog(body, context, "game-java-crash.txt", "last Java exception", launch);
            addLog(body, context, "core-load/runtime-status.txt", "launcher status", launch);
            addLog(body, context, "core-load/sdl-launcher-output.txt", "launcher output", launch);
            addLog(body, context, "core-load/input-diagnostic.txt", "controller and display diagnostic", launch);
            addLog(body, context, "android-gpu-diagnostic.txt", "Android GPU capability diagnostic", 0);
            addLog(body, context, "core-load/.utpg/System/UnrealTournament.log", "game log", launch);
            if (selected != null) body.append("\n--- Android logcat for last game PID (best effort) ---\n").append(logcat(selected.getPid()));
            return writeReport(context, body.toString(), selected, selected != null ? selected.getPid() : 0);
        }
    }

    private static void capturePending(Context c) {
        long launch = prefs(c).getLong("launch", 0);
        if (launch == 0 || prefs(c).getLong("reported", 0) == launch) return;
        try {
            ActivityManager manager = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            List<ApplicationExitInfo> exits = manager.getHistoricalProcessExitReasons(c.getPackageName(), 0, 32);
            ApplicationExitInfo selected = null;
            for (ApplicationExitInfo info : exits) {
                if ((c.getPackageName() + ":utexperiment").equals(info.getProcessName())
                        && info.getTimestamp() >= launch - 1000 && isFailure(info)) {
                    if (selected == null || info.getTimestamp() > selected.getTimestamp()) selected = info;
                }
            }
            File javaCrash = privateFile(c, "game-java-crash.txt");
            File status = privateFile(c, "core-load/runtime-status.txt");
            File gameLog = privateFile(c, "core-load/.utpg/System/UnrealTournament.log");
            boolean javaFailure = javaCrash.isFile() && javaCrash.lastModified() >= launch - 1000;
            boolean returned = status.isFile() && status.lastModified() >= launch - 1000;
            boolean criticalLog = gameLog.isFile() && gameLog.lastModified() >= launch - 1000
                    && tail(gameLog, MAX_LOG).contains("Critical:");
            // The legacy launcher can report a fatal engine error yet return 0.
            boolean returnedFailure = returned && (!tail(status, 4096).trim().equals("SDL-hosted launcher returned 0") || criticalLog);
            if (selected == null && !javaFailure && !returnedFailure) return;
            StringBuilder body = new StringBuilder();
            body.append("Game launch: ").append(new Date(launch)).append('\n');
            int pid = 0;
            if (selected != null) {
                pid = selected.getPid();
                body.append("Android exit reason: ").append(reason(selected.getReason())).append(" (").append(selected.getReason()).append(")\n");
                body.append("Status/signal: ").append(selected.getStatus()).append("\nPID: ").append(pid).append('\n');
                body.append("Exit time: ").append(new Date(selected.getTimestamp())).append('\n');
                body.append("Description: ").append(selected.getDescription()).append('\n');
            } else body.append("Android exit details not available yet.\n");
            if (javaFailure) body.append("\n--- Java exception ---\n").append(tail(javaCrash, MAX_LOG));
            if (returnedFailure) body.append("\n--- Native launcher status ---\n").append(tail(status, 4096));
            addLog(body, c, "core-load/sdl-launcher-output.txt", "launcher output", launch);
            addLog(body, c, "core-load/input-diagnostic.txt", "controller and display diagnostic", launch);
            addLog(body, c, "core-load/.utpg/System/UnrealTournament.log", "game log", launch);
            if (pid > 0) body.append("\n--- Android logcat for game PID (best effort) ---\n").append(logcat(pid));
            writeReport(c, body.toString(), selected, pid);
            prefs(c).edit().putLong("reported", launch).commit();
        } catch (Exception e) { Log.e(TAG, "Crash report collection failed; will retry", e); }
    }

    private static boolean isFailure(ApplicationExitInfo info) {
        int reason = info.getReason();
        return reason == ApplicationExitInfo.REASON_CRASH || reason == ApplicationExitInfo.REASON_CRASH_NATIVE
                || reason == ApplicationExitInfo.REASON_ANR || reason == ApplicationExitInfo.REASON_SIGNALED
                || (reason == ApplicationExitInfo.REASON_EXIT_SELF && info.getStatus() != 0);
    }
    private static String reason(int r) {
        if (r == ApplicationExitInfo.REASON_CRASH) return "Java crash";
        if (r == ApplicationExitInfo.REASON_CRASH_NATIVE) return "native crash";
        if (r == ApplicationExitInfo.REASON_ANR) return "application not responding";
        if (r == ApplicationExitInfo.REASON_SIGNALED) return "terminated by signal";
        if (r == ApplicationExitInfo.REASON_EXIT_SELF) return "nonzero process exit";
        return "other";
    }
    private static void addLog(StringBuilder out, Context c, String path, String label, long launch) throws IOException {
        File f = privateFile(c, path);
        out.append("\n--- ").append(label).append(" ---\n");
        if (!f.isFile()) { out.append("Not available.\n"); return; }
        if (f.lastModified() < launch - 1000) out.append("This log predates this launch.\n");
        out.append(tail(f, MAX_LOG));
    }
    private static String tail(File file, int limit) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            long start = Math.max(0, in.length() - limit);
            in.seek(start);
            byte[] data = new byte[(int) (in.length() - start)];
            in.readFully(data);
            return (start > 0 ? "[Earlier output omitted]\n" : "") + new String(data, StandardCharsets.UTF_8);
        }
    }
    private static String logcat(int pid) {
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/logcat", "-d", "-v", "threadtime", "--pid=" + pid, "-t", "800")
                    .redirectErrorStream(true).start();
            byte[] bytes = readBounded(process.getInputStream(), MAX_LOG);
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroy();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) { return "Unavailable: " + e + "\n"; }
        finally { if (process != null) process.destroy(); }
    }
    private static byte[] readBounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while (out.size() < limit && (n = input.read(buffer, 0, Math.min(buffer.length, limit - out.size()))) != -1)
            out.write(buffer, 0, n);
        return out.toByteArray();
    }
    private static String writeReport(Context c, String body, ApplicationExitInfo exit, int pid) throws IOException {
        File dir = reports(c);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create Reports folder");
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(new Date());
        String base = "UT99-" + stamp + "-" + android.os.Process.myPid();
        StringBuilder header = new StringBuilder("UT99 Android diagnostic report\n");
        header.append("Generated: ").append(new Date()).append('\n');
        header.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        header.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        try {
            android.content.pm.PackageInfo info = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            header.append("App: ").append(info.versionName).append(" (version code ").append(info.getLongVersionCode()).append(")\n");
        } catch (Exception e) { header.append("App version unavailable: ").append(e).append('\n'); }
        header.append("ABIs: ").append(java.util.Arrays.toString(Build.SUPPORTED_ABIS)).append('\n');
        try { header.append("Memory page size: ").append(Os.sysconf(OsConstants._SC_PAGESIZE)).append('\n'); }
        catch (Exception e) { header.append("Memory page size unavailable: ").append(e).append('\n'); }
        if (exit != null) {
            try (InputStream trace = exit.getTraceInputStream()) {
                if (trace != null) {
                    boolean nativeCrash = exit.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE;
                    String traceName = base + (nativeCrash ? ".native.pb" : ".trace.txt");
                    Files.write(new File(dir, traceName).toPath(), readBounded(trace, MAX_TRACE));
                    header.append("Android trace: ").append(traceName).append(" (up to ").append(MAX_TRACE).append(" bytes)\n");
                } else header.append("Android trace: unavailable\n");
            } catch (Exception e) { header.append("Android trace unavailable: ").append(e).append('\n'); }
        }
        File temp = File.createTempFile("ut99-report-", ".tmp", dir);
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write((header + "\n" + body).getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            Files.move(temp.toPath(), new File(dir, base + ".txt").toPath(), StandardCopyOption.REPLACE_EXISTING);
            Log.i(TAG, "Saved diagnostic report in " + dir.getAbsolutePath());
        } finally { temp.delete(); }
        return base + ".txt";
    }
}
