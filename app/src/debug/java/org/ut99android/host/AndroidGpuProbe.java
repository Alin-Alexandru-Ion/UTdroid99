package org.ut99android.host;

import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Android-side capability diagnostic; it does not accelerate the Linux process. */
final class AndroidGpuProbe {
    private AndroidGpuProbe() {}
    static String record(Context context) {
        String report;
        try { report = inspect(context); }
        catch (RuntimeException error) { report = "Android GPU probe failed: " + error; }
        try { Files.write(new File(context.getFilesDir(), "android-gpu-diagnostic.txt").toPath(), report.getBytes(StandardCharsets.UTF_8)); }
        catch (IOException ignored) { }
        android.util.Log.i("UT99Gpu", report);
        return report;
    }
    private static String inspect(Context context) {
        StringBuilder text = new StringBuilder("Android GPU capability probe (not the Linux renderer)\n");
        FeatureInfo[] features = context.getPackageManager().getSystemAvailableFeatures();
        if (features != null) for (FeatureInfo feature : features) {
            if (PackageManager.FEATURE_VULKAN_HARDWARE_VERSION.equals(feature.name)) {
                int v = feature.version;
                text.append("Android Vulkan feature version: ").append((v >>> 22) & 0x7f).append('.')
                        .append((v >>> 12) & 0x3ff).append('.').append(v & 0xfff).append('\n');
            }
        }
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) return text.append("EGL display unavailable\n").toString();
        EGLSurface surface = EGL14.EGL_NO_SURFACE;
        EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
        boolean initialized = false;
        try {
            int[] version = new int[2];
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return text.append("EGL initialization failed\n").toString();
            initialized = true;
            int[] attributes = {EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_NONE};
            EGLConfig[] configs = new EGLConfig[1]; int[] count = new int[1];
            if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) || count[0] == 0)
                return text.append("No EGL pbuffer configuration\n").toString();
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
            eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            if (surface == EGL14.EGL_NO_SURFACE || eglContext == EGL14.EGL_NO_CONTEXT || !EGL14.eglMakeCurrent(display, surface, surface, eglContext))
                return text.append("Could not create EGL context\n").toString();
            text.append("Android EGL version: ").append(version[0]).append('.').append(version[1]).append('\n');
            text.append("Android OpenGL ES vendor: ").append(GLES20.glGetString(GLES20.GL_VENDOR)).append('\n');
            text.append("Android OpenGL ES renderer: ").append(GLES20.glGetString(GLES20.GL_RENDERER)).append('\n');
            text.append("Android OpenGL ES version: ").append(GLES20.glGetString(GLES20.GL_VERSION)).append('\n');
            return text.toString();
        } finally {
            if (initialized) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext);
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
                EGL14.eglTerminate(display);
            }
        }
    }
}
