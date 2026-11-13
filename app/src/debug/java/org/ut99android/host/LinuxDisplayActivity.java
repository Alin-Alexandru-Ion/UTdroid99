package org.ut99android.host;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays a localhost-only Linux framebuffer and forwards touchscreen mouse events. */
public final class LinuxDisplayActivity extends Activity {
    private final ExecutorService inputQueue = Executors.newSingleThreadExecutor();
    private volatile LocalFramebufferClient client;
    private volatile boolean closing;
    private FrameView frame;
    private TextView status;
    private final java.util.ArrayList<View> controls = new java.util.ArrayList<>();
    private final java.util.HashSet<Integer> heldKeys = new java.util.HashSet<>();
    private boolean fireHeld;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        FrameLayout layout = new FrameLayout(this);
        frame = new FrameView(); layout.addView(frame, new FrameLayout.LayoutParams(-1, -1));
        status = new TextView(this); status.setTextColor(Color.WHITE); status.setBackgroundColor(0xaa000000);
        status.setText("Connecting to the local Linux display…");
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER); layout.addView(status, sp);
        Button menu = new Button(this); menu.setText("Menu"); menu.setAllCaps(false);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        layout.addView(menu, mp);
        menu.setOnClickListener(v -> sendKey(0xff1b));
        addGameplayControls(layout);
        setContentView(layout);
        new Thread(() -> AndroidGpuProbe.record(getApplicationContext()), "android-gpu-probe").start();
        new Thread(() -> {
            int port = getIntent().getIntExtra("display_port", 5901);
            try (LocalFramebufferClient connection = new LocalFramebufferClient(port)) {
                client = connection;
                connection.receive((w, h, pixels) -> {
                    frame.update(w, h, pixels);
                    runOnUiThread(() -> status.setVisibility(View.GONE));
                });
            } catch (IOException e) {
                if (!closing) runOnUiThread(() -> { status.setText("Linux display disconnected: " + e.getMessage()); status.setVisibility(View.VISIBLE); });
            } finally { client = null; }
        }, "linux-display").start();
    }
    private void sendKey(int keysym) {
        inputQueue.execute(() -> {
            LocalFramebufferClient c = client;
            if (c != null) try { c.key(keysym, true); c.key(keysym, false); } catch (IOException ignored) { }
        });
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void setHeldKey(int keysym, boolean down) {
        if (down ? !heldKeys.add(keysym) : !heldKeys.remove(keysym)) return;
        inputQueue.execute(() -> { LocalFramebufferClient c = client; if (c != null) try { c.key(keysym, down); } catch (IOException ignored) { } });
    }
    private void setFire(boolean down) {
        if (fireHeld == down) return;
        fireHeld = down;
        inputQueue.execute(() -> { LocalFramebufferClient c = client; if (c != null) try { c.centerButton(down); } catch (IOException ignored) { } });
    }
    private void releaseControls() {
        for (int key : new java.util.ArrayList<>(heldKeys)) setHeldKey(key, false);
        setFire(false);
    }
    private void addControl(FrameLayout layout, String label, int key, int column, int row, boolean right) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setPadding(0, 0, 0, 0);
        int size = dp(56), margin = dp(8);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, Gravity.BOTTOM | (right ? Gravity.END : Gravity.START));
        if (right) params.rightMargin = margin + column * (size + margin);
        else params.leftMargin = margin + column * (size + margin);
        params.bottomMargin = margin + row * (size + margin);
        button.setOnTouchListener((v, e) -> {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) { if (key == 0) setFire(true); else setHeldKey(key, true); }
            else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) { if (key == 0) setFire(false); else setHeldKey(key, false); }
            return true;
        });
        button.setVisibility(View.GONE); controls.add(button); layout.addView(button, params);
    }
    private void addGameplayControls(FrameLayout layout) {
        addControl(layout, "↑", 0xff52, 1, 2, false);
        addControl(layout, "←", 0xff51, 0, 1, false);
        addControl(layout, "↓", 0xff54, 1, 1, false);
        addControl(layout, "→", 0xff53, 2, 1, false);
        addControl(layout, "Fire", 0, 0, 1, true);
        addControl(layout, "Jump", 0x20, 1, 1, true);
        Button toggle = new Button(this); toggle.setText("Show controls"); toggle.setAllCaps(false);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        layout.addView(toggle, params);
        toggle.setOnClickListener(v -> {
            boolean show = controls.get(0).getVisibility() != View.VISIBLE;
            releaseControls();
            for (View control : controls) control.setVisibility(show ? View.VISIBLE : View.GONE);
            toggle.setText(show ? "Hide controls" : "Show controls");
        });
    }
    @Override protected void onPause() { releaseControls(); super.onPause(); }
    @Override protected void onDestroy() {
        closing = true;
        LocalFramebufferClient c = client;
        if (c != null) try { c.close(); } catch (IOException ignored) { }
        inputQueue.shutdownNow();
        super.onDestroy();
    }
    private final class FrameView extends View {
        private final Object lock = new Object();
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private Bitmap bitmap;
        private final RectF destination = new RectF();
        private int frameWidth, frameHeight;
        private int activePointer = -1;
        private int lastX, lastY;
        FrameView() { super(LinuxDisplayActivity.this); setBackgroundColor(Color.BLACK); }
        void update(int width, int height, int[] pixels) {
            synchronized (lock) {
                if (bitmap == null || width != frameWidth || height != frameHeight) {
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    frameWidth = width; frameHeight = height;
                }
                bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            }
            postInvalidateOnAnimation();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            synchronized (lock) {
                if (bitmap == null) return;
                float scale = Math.min((float)getWidth() / frameWidth, (float)getHeight() / frameHeight);
                float w = frameWidth * scale, h = frameHeight * scale;
                destination.set((getWidth() - w) / 2, (getHeight() - h) / 2, (getWidth() + w) / 2, (getHeight() + h) / 2);
                canvas.drawBitmap(bitmap, null, destination, paint);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_CANCEL && activePointer != -1) {
                final int px = lastX, py = lastY;
                inputQueue.execute(() -> { LocalFramebufferClient c = client; if (c != null) try { c.pointer(px, py, false); } catch (IOException ignored) { } });
                activePointer = -1;
                return true;
            }
            if (action == MotionEvent.ACTION_DOWN) {
                synchronized (lock) { if (!destination.contains(e.getX(), e.getY())) return true; }
                activePointer = e.getPointerId(0);
            }
            int index = e.findPointerIndex(activePointer);
            if (index < 0) return true;
            float x = e.getX(index), y = e.getY(index);
            final int px, py;
            synchronized (lock) {
                if (bitmap == null || destination.width() <= 0 || destination.height() <= 0) return true;
                px = Math.round((x - destination.left) * frameWidth / destination.width());
                py = Math.round((y - destination.top) * frameHeight / destination.height());
            }
            lastX = px; lastY = py;
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                boolean pressed = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE;
                inputQueue.execute(() -> {
                    LocalFramebufferClient c = client;
                    if (c == null) return;
                    try {
                        // The legacy menu needs the pointer position to settle before a click.
                        if (action == MotionEvent.ACTION_DOWN) { c.pointer(px, py, false); Thread.sleep(600); }
                        if (action == MotionEvent.ACTION_UP) Thread.sleep(400);
                        c.pointer(px, py, pressed);
                    } catch (IOException ignored) { } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                });
                if (!pressed) activePointer = -1;
            }
            return true;
        }
    }
}
