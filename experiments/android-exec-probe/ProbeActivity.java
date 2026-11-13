package org.ut99android.execprobe;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Arrays;

public final class ProbeActivity extends Activity {
    private Process standaloneGuest(File proot, File qemu, File root, String output, String... command) throws Exception {
        ArrayList<String> args = new ArrayList<>(Arrays.asList(proot.getAbsolutePath(), "-0", "-L", "-q", qemu.getAbsolutePath(),
                "-r", root.getAbsolutePath(), "-w", "/opt/ut99/SystemARM64", "-b", "/proc:/proc", "-b", "/data:/data",
                "-b", "/system:/system", "-b", "/apex:/apex", "-b", "/dev:/dev", "-b", "/sys:/sys"));
        args.addAll(Arrays.asList(command));
        ProcessBuilder b = new ProcessBuilder(args);
        b.environment().put("LD_LIBRARY_PATH", getFilesDir().getAbsolutePath());
        b.environment().put("PROOT_TMP_DIR", getCacheDir().getAbsolutePath());
        b.environment().put("PROOT_LOADER", new File(getApplicationInfo().nativeLibraryDir, "libprootloader.so").getAbsolutePath());
        b.environment().put("QEMU_SET_ENV", "LD_LIBRARY_PATH=.");
        b.environment().put("DISPLAY", ":98");
        b.environment().put("SDL_VIDEODRIVER", "x11");
        b.environment().put("SDL_AUDIODRIVER", "dummy");
        b.environment().put("LIBGL_ALWAYS_SOFTWARE", "1");
        b.redirectErrorStream(true); b.redirectOutput(new File(getFilesDir(), output));
        return b.start();
    }
    private String run(String... command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            builder.environment().put("LD_LIBRARY_PATH", getFilesDir().getAbsolutePath());
            builder.environment().put("PROOT_TMP_DIR", getCacheDir().getAbsolutePath());
            builder.environment().put("PROOT_LOADER", new File(getApplicationInfo().nativeLibraryDir, "libprootloader.so").getAbsolutePath());
            Process process = builder.start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "timeout";
            }
            return "exit=" + process.exitValue() + " output=" + new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (Exception error) {
            return error.getClass().getSimpleName() + ": " + error.getMessage();
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            String result;
            try {
                File packaged = new File(getApplicationInfo().nativeLibraryDir, "libexecfixture.so");
                File copied = new File(getFilesDir(), "exec-fixture");
                Files.copy(packaged.toPath(), copied.toPath(), StandardCopyOption.REPLACE_EXISTING);
                copied.setReadable(true, true);
                copied.setExecutable(true, true);
                File guest = new File(getFilesDir(), "guest-libc.so");
                try (java.io.InputStream in = getAssets().open("guest-libc.so")) {
                    Files.copy(in, guest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                guest.setReadOnly();
                File loader = new File(getApplicationInfo().nativeLibraryDir, "libguestloader.so");
                for (String name : new String[]{"libtalloc.so.2", "libandroid-shmem.so"}) {
                    File destination = new File(getFilesDir(), name);
                    try (java.io.InputStream in = getAssets().open(name)) { Files.copy(in, destination.toPath(), StandardCopyOption.REPLACE_EXISTING); }
                    destination.setReadOnly();
                }
                File proot = new File(getApplicationInfo().nativeLibraryDir, "libprobeproot.so");
                File qemu = new File(getApplicationInfo().nativeLibraryDir, "libqemuprobe.so");
                for (String name : getAssets().list("qemu")) {
                    File destination = new File(getFilesDir(), name);
                    try (java.io.InputStream in = getAssets().open("qemu/" + name)) { Files.copy(in, destination.toPath(), StandardCopyOption.REPLACE_EXISTING); }
                    destination.setReadOnly();
                }
                File root = new File(getFilesDir(), "linux-root");
                File lib = new File(root, "lib"); lib.mkdirs();
                File guestRoot = new File(lib, "libc.so.6");
                Files.copy(guest.toPath(), guestRoot.toPath(), StandardCopyOption.REPLACE_EXISTING); guestRoot.setReadOnly();
                new File(root, "ldhost").createNewFile();
                File armRoot = new File(getFilesDir(), "arm-root");
                for (String[] pair : new String[][]{{"arm-uname", "bin/uname"}, {"arm-ld-linux.so", "lib/ld-linux-aarch64.so.1"},
                        {"arm-libc.so", "lib/aarch64-linux-gnu/libc.so.6"}}) {
                    File destination = new File(armRoot, pair[1]); destination.getParentFile().mkdirs();
                    try (java.io.InputStream in = getAssets().open(pair[0])) { Files.copy(in, destination.toPath(), StandardCopyOption.REPLACE_EXISTING); }
                    destination.setExecutable(true, true); destination.setReadOnly();
                }
                result = "system: " + run("/system/bin/echo", "baseline")
                    + "\nAPK native library: " + run(packaged.getAbsolutePath())
                    + "\nwritable app data copy: " + run(copied.getAbsolutePath())
                    + "\npackaged glibc loader + read-only Linux guest: "
                    + run(loader.getAbsolutePath(), "--library-path", getFilesDir().getAbsolutePath(), guest.getAbsolutePath())
                    + "\ntraced glibc loader: " + run(packaged.getAbsolutePath(), "--trace", loader.getAbsolutePath(),
                        "--library-path", getFilesDir().getAbsolutePath(), guest.getAbsolutePath())
                    + "\npackaged proot: " + run(proot.getAbsolutePath(), "--version")
                    + "\npackaged proot + loader: " + run(proot.getAbsolutePath(), "-r", root.getAbsolutePath(),
                        "-b", loader.getAbsolutePath() + ":/ldhost", "/ldhost", "--library-path", "/lib", "/lib/libc.so.6")
                    + "\npackaged proot + ARM64 QEMU: " + run(proot.getAbsolutePath(), "-q", qemu.getAbsolutePath(),
                        "-r", armRoot.getAbsolutePath(), "-b", "/proc:/proc", "-b", "/data:/data",
                        "-b", "/system:/system", "-b", "/apex:/apex", "/bin/uname", "-m");
                if (getIntent().getBooleanExtra("standalone_game", false)) {
                    File fullRoot = new File(getFilesDir(), "full-root");
                    String working = "/opt/ut99/SystemARM64";
                    ProcessBuilder b = new ProcessBuilder(proot.getAbsolutePath(), "-0", "-L", "-q", qemu.getAbsolutePath(),
                            "-r", fullRoot.getAbsolutePath(), "-w", working, "-b", "/proc:/proc", "-b", "/data:/data",
                            "-b", "/system:/system", "-b", "/apex:/apex", "-b", "/dev:/dev", "-b", "/sys:/sys",
                            working + "/ut-bin-arm64", "DM-Agony.unr?game=Botpack.DeathMatchPlus", "-log");
                    b.environment().put("LD_LIBRARY_PATH", getFilesDir().getAbsolutePath());
                    b.environment().put("PROOT_TMP_DIR", getCacheDir().getAbsolutePath());
                    b.environment().put("PROOT_LOADER", new File(getApplicationInfo().nativeLibraryDir, "libprootloader.so").getAbsolutePath());
                    b.environment().put("QEMU_SET_ENV", "LD_LIBRARY_PATH=.");
                    b.environment().put("SDL_VIDEODRIVER", "dummy");
                    b.environment().put("SDL_AUDIODRIVER", "dummy");
                    b.redirectErrorStream(true); b.redirectOutput(new File(getFilesDir(), "standalone-game.log"));
                    Process game = b.start();
                    result += "\nstandalone game test started";
                    new Thread(() -> {
                        try { if (!game.waitFor(120, TimeUnit.SECONDS)) game.destroyForcibly(); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); game.destroyForcibly(); }
                    }).start();
                }
                if (getIntent().getBooleanExtra("standalone_display", false)) {
                    File fullRoot = new File(getFilesDir(), "full-root");
                    Process display = standaloneGuest(proot, qemu, fullRoot, "standalone-xvfb.log", "/usr/bin/Xvfb", ":98", "-screen", "0", "1024x768x24", "-nolisten", "tcp", "-nolock", "-ac");
                    Thread.sleep(5000);
                    Process info = standaloneGuest(proot, qemu, fullRoot, "standalone-xinfo.log", "/usr/bin/xdpyinfo", "-display", ":98", "-ext", "GLX");
                    info.waitFor(10, TimeUnit.SECONDS);
                    Process vnc = standaloneGuest(proot, qemu, fullRoot, "standalone-vnc.log", "/usr/bin/x11vnc", "-display", ":98", "-localhost", "-forever", "-shared", "-rfbport", "5902", "-nopw", "-noxdamage", "-noshm");
                    Thread.sleep(5000);
                    Process game = standaloneGuest(proot, qemu, fullRoot, "standalone-display-game.log", "/opt/ut99/SystemARM64/ut-bin-arm64", "DM-Agony.unr?game=Botpack.DeathMatchPlus", "-log");
                    result += "\nstandalone display and game test started";
                    new Thread(() -> {
                        try { game.waitFor(180, TimeUnit.SECONDS); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        game.destroyForcibly(); vnc.destroyForcibly(); display.destroyForcibly();
                    }).start();
                }
            } catch (Exception error) { result = "setup error: " + error; }
            Log.i("UTExecProbe", result);
            final String text = result;
            runOnUiThread(() -> { TextView view = new TextView(this); view.setText(text); setContentView(view); });
        }).start();
    }
}
