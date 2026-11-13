package org.ut99android.armprobe;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

/** Isolated experiment: run an unmodified ARM64 Linux program using an APK-contained runtime. */
public final class ArmProbeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            String result;
            try {
                File root = new File(getFilesDir(), "root");
                for (String[] pair : new String[][]{{"arm-uname", "bin/uname"},
                        {"arm-ld-linux.so", "lib/ld-linux-aarch64.so.1"},
                        {"arm-libc.so", "lib/aarch64-linux-gnu/libc.so.6"}}) {
                    File destination = new File(root, pair[1]);
                    destination.getParentFile().mkdirs();
                    try (java.io.InputStream in = getAssets().open(pair[0])) {
                        Files.copy(in, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                    destination.setReadable(true, true);
                    destination.setExecutable(true, true);
                    destination.setReadOnly();
                }
                for (String name : new String[]{"libtalloc.so.2", "libandroid-shmem.so"}) {
                    File dst = new File(getFilesDir(), name);
                    try (java.io.InputStream in = getAssets().open(name)) {
                        Files.copy(in, dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                    dst.setReadOnly();
                }
                File nativeDir = new File(getApplicationInfo().nativeLibraryDir);
                ProcessBuilder builder = new ProcessBuilder(new File(nativeDir, "libutproot.so").getAbsolutePath(),
                    "-0", "-L", "-r", root.getAbsolutePath(), "-b", "/proc:/proc", "-b", "/data:/data",
                    "-b", "/system:/system", "-b", "/apex:/apex", "/bin/uname", "-m");
                builder.environment().put("LD_LIBRARY_PATH", getFilesDir().getAbsolutePath());
                builder.environment().put("PROOT_TMP_DIR", getCacheDir().getAbsolutePath());
                builder.environment().put("PROOT_LOADER", new File(nativeDir, "libutprootloader.so").getAbsolutePath());
                builder.redirectErrorStream(true);
                Process p = builder.start();
                if (!p.waitFor(20, TimeUnit.SECONDS)) { p.destroyForcibly(); result = "timeout"; }
                else result = "exit=" + p.exitValue() + " output=" + new String(p.getInputStream().readAllBytes()).trim();
            } catch (Exception e) { result = "error: " + e; }
            Log.i("UTArmProbe", result);
            final String text = result;
            runOnUiThread(() -> { TextView view = new TextView(this); view.setText(text); setContentView(view); });
        }).start();
    }
}
