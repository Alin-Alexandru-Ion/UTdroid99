package org.ut99android.host;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import java.io.File;

public final class MainActivity extends Activity implements SurfaceHolder.Callback {
    static { System.loadLibrary("ut99host"); }
    private static native void renderFrame(Surface surface);
    private static native void lifecycle(String event);
    private static native String runProbe(String path, boolean original);
    private static native String probeSdlCore(String directory);
    private static native String probeDlBridge(String directory);
    private static native String probeSdlDlCore(String directory);
    private static native String probeCxx(String directory, boolean withCore);
    private static native String probeMath(String directory);
    private static native String probeGcc(String directory);
    private static native String probeErrno(String directory);
    private static native String probeMutex(String directory);
    private static native String probeCondition(String directory);
    private static native String probeCtype(String directory);
    private static native String probeStat(String directory);
    private static native String probeFormat(String directory);
    private static native String probePartial(String directory);
    private static native String probeExperimentalCore(String directory, boolean loadEngine, boolean loadLauncher, boolean runMain);
    private static native String probeJumpBaseline(String directory);
    private static native String probeEventLink(String directory);
    private static native String probeInlineRtti(String directory, boolean globalThrower);
    private static native String probeReferenceRtti(String directory);
    private static native String probeLauncherPath(String directory);
    private static native String probeMemoryLoader(String sourcePath);
    private static native String probeDirectOriginal(String directory);
    static native String probeDirectMemory(String directory, byte[] image);
    static native String registerAdaptedImage(String name, byte[] image);
    static native String probeRegisteredModules(String directory);
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0 && BuildConfig.LINUX_RUNTIME_PROBE
                && getIntent().getBooleanExtra("linux_display_fixture", false)) {
            Intent display = new Intent();
            display.setClassName(this, "org.ut99android.host.LinuxDisplayActivity");
            display.putExtra("display_port", getIntent().getIntExtra("display_port", 5901));
            startActivity(display); finish(); return;
        }
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0
                && getIntent().getStringExtra("probe") == null
                && !getIntent().getBooleanExtra("legacy_diagnostics", false)) {
            Intent setup = new Intent();
            setup.setClassName(this, "org.ut99android.host.StorageSetupActivity");
            setup.putExtra("verify_runtime", getIntent().getBooleanExtra("verify_runtime", false));
            startActivity(setup);
            finish();
            return;
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        lifecycle("create");
        FrameLayout root = new FrameLayout(this);
        SurfaceView surface = new SurfaceView(this);
        surface.getHolder().addCallback(this);
        root.addView(surface);
        TextView text = new TextView(this);
        text.setText("UT99 Android • loader diagnostic\nEngine has not been started.");
        text.setTextColor(Color.WHITE); text.setTextSize(20); text.setPadding(32, 24, 32, 24);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.addView(text);
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
        Button sharedStorage = new Button(this);
        sharedStorage.setText("Set up phone storage and start UT99");
        sharedStorage.setOnClickListener(v -> {
            Intent setup = new Intent();
            setup.setClassName(this, "org.ut99android.host.StorageSetupActivity");
            startActivity(setup);
        });
        controls.addView(sharedStorage);
        Button runtime = new Button(this);
        runtime.setText("Verify included runtime");
        runtime.setOnClickListener(v -> {
            Intent setup = new Intent();
            setup.setClassName(this, "org.ut99android.host.StorageSetupActivity");
            setup.putExtra("verify_runtime", true);
            startActivity(setup);
        });
        controls.addView(runtime);
        if (getIntent().getBooleanExtra("legacy_diagnostics", false)) {
        Button fixture = new Button(this);
        fixture.setText("Run loader fixture");
        fixture.setOnClickListener(v -> text.setText(runProbe("libloaderprobe.so", false)));
        controls.addView(fixture);
        Button engine = new Button(this);
        engine.setText("Probe local Core.so");
        engine.setOnClickListener(v -> text.setText(runProbe(new File(getFilesDir(), "Core.so").getAbsolutePath(), true)));
        controls.addView(engine);
        Button sdl = new Button(this);
        sdl.setText("Probe SDL and Core");
        sdl.setOnClickListener(v -> text.setText(probeSdlCore(getFilesDir().getAbsolutePath())));
        controls.addView(sdl);
        Button bridge = new Button(this);
        bridge.setText("Test dl bridge");
        bridge.setOnClickListener(v -> text.setText(probeDlBridge(getFilesDir().getAbsolutePath())));
        controls.addView(bridge);
        Button combined = new Button(this);
        combined.setText("Probe SDL, dl and Core");
        combined.setOnClickListener(v -> text.setText(probeSdlDlCore(getFilesDir().getAbsolutePath())));
        controls.addView(combined);
        Button cxx = new Button(this);
        cxx.setText("Test C++ fixture");
        cxx.setOnClickListener(v -> text.setText(probeCxx(getFilesDir().getAbsolutePath(), false)));
        controls.addView(cxx);
        Button coreCxx = new Button(this);
        coreCxx.setText("Probe Core with C++");
        coreCxx.setOnClickListener(v -> text.setText(probeCxx(getFilesDir().getAbsolutePath(), true)));
        controls.addView(coreCxx);
        }
        String requested = getIntent().getStringExtra("probe");
        if ("memory_loader".equals(requested)) text.setText(probeMemoryLoader(new File(getApplicationInfo().nativeLibraryDir, "libmemfixture.so").getAbsolutePath()));
        if ("direct_original".equals(requested)) text.setText(probeDirectOriginal(getFilesDir().getAbsolutePath()));
        if ("direct_memory".equals(requested)) {
            try { text.setText((String)Class.forName("org.ut99android.host.DirectMemoryProbe").getMethod("run", String.class).invoke(null, getFilesDir().getAbsolutePath())); }
            catch (ReflectiveOperationException e) { text.setText("Direct memory probe unavailable: " + e); }
        }
        if ("direct_registered".equals(requested)) {
            try { text.setText((String)Class.forName("org.ut99android.host.DirectMemoryProbe").getMethod("runRegistered", String.class).invoke(null, getFilesDir().getAbsolutePath())); }
            catch (ReflectiveOperationException e) { text.setText("Registered probe unavailable: " + e); }
        }
        if ("launcher_path".equals(requested)) text.setText(probeLauncherPath(getFilesDir().getAbsolutePath()));
        if ("math".equals(requested)) text.setText(probeMath(getFilesDir().getAbsolutePath()));
        if ("gcc".equals(requested)) text.setText(probeGcc(getFilesDir().getAbsolutePath()));
        if ("errno".equals(requested)) text.setText(probeErrno(getFilesDir().getAbsolutePath()));
        if ("mutex".equals(requested)) text.setText(probeMutex(getFilesDir().getAbsolutePath()));
        if ("condition".equals(requested)) text.setText(probeCondition(getFilesDir().getAbsolutePath()));
        if ("ctype".equals(requested)) text.setText(probeCtype(getFilesDir().getAbsolutePath()));
        if ("stat".equals(requested)) text.setText(probeStat(getFilesDir().getAbsolutePath()));
        if ("format".equals(requested)) text.setText(probeFormat(getFilesDir().getAbsolutePath()));
        if ("partial".equals(requested)) text.setText(probePartial(getFilesDir().getAbsolutePath()));
        if ("core_load".equals(requested)) text.setText(probeExperimentalCore(getFilesDir().getAbsolutePath(), false, false, false));
        if ("engine_load".equals(requested)) text.setText(probeExperimentalCore(getFilesDir().getAbsolutePath(), true, false, false));
        if ("launcher_load".equals(requested)) text.setText(probeExperimentalCore(getFilesDir().getAbsolutePath(), true, true, false));
        if ("launcher_start".equals(requested)) text.setText(probeExperimentalCore(getFilesDir().getAbsolutePath(), true, true, true));
        if ("jump_baseline".equals(requested)) text.setText(probeJumpBaseline(getFilesDir().getAbsolutePath()));
        if ("event".equals(requested)) text.setText(probeEventLink(getFilesDir().getAbsolutePath()));
        if ("inline".equals(requested)) text.setText(probeInlineRtti(getFilesDir().getAbsolutePath(), false));
        if ("inline_global".equals(requested)) text.setText(probeInlineRtti(getFilesDir().getAbsolutePath(), true));
        if ("inline_reference".equals(requested)) text.setText(probeReferenceRtti(getFilesDir().getAbsolutePath()));
        if ("cxx".equals(requested)) text.setText(probeCxx(getFilesDir().getAbsolutePath(), false));
        if ("core".equals(requested)) text.setText(probeCxx(getFilesDir().getAbsolutePath(), true));
        }
        root.addView(controls, params); setContentView(root);
    }
    @Override public void surfaceCreated(SurfaceHolder holder) { lifecycle("surface-created"); renderFrame(holder.getSurface()); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { lifecycle("surface-changed"); renderFrame(holder.getSurface()); }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { lifecycle("surface-destroyed"); }
    @Override protected void onResume() {
        super.onResume(); lifecycle("resume");
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            try { Class.forName("org.ut99android.host.CrashReporter").getMethod("check", android.content.Context.class).invoke(null, this); }
            catch (ReflectiveOperationException e) { Log.w("UT99Host", "Crash reporting unavailable", e); }
        }
    }
    @Override protected void onPause() { lifecycle("pause"); super.onPause(); }
    @Override protected void onDestroy() { lifecycle("destroy"); super.onDestroy(); }
}
