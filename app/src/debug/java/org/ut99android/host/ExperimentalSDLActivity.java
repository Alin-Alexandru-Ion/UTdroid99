package org.ut99android.host;

import android.content.pm.ApplicationInfo;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.SurfaceHolder;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import java.util.HashMap;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLSurface;
import org.libsdl.app.SDLControllerManager;
import android.content.Context;
import android.app.AlertDialog;
import android.view.WindowInsets;

/** Isolated debug-only research host for the original ARM64 launcher. */
public class ExperimentalSDLActivity extends SDLActivity {
    private static native boolean enableTextInput(String directory);
    private static native void setCameraSensitivity(float multiplier);
    private static native boolean isCameraSensitivityReady();
    private static native void setTouchAxis(int axis, float value);
    private static native void setTouchButton(int button, boolean pressed);
    private static native boolean isTouchJoystickReady();
    private boolean touchMode;
    private android.hardware.input.InputManager.InputDeviceListener deviceListener;
    private TouchStickView movementStick;
    private final java.util.ArrayList<View> gameplayViews = new java.util.ArrayList<>();
    private final Handler overlayQueue = new Handler(Looper.getMainLooper());
    private int aimPointer = -1;
    private float aimStartX, aimStartY, aimLastX, aimLastY;
    private boolean aiming, overlayMenuDown;
    private final Runnable stopAim = () -> { setTouchAxis(2, 0); setTouchAxis(3, 0); };

    private boolean hasPhysicalController() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d != null && !d.isVirtual() && d.isExternal() &&
                    ((d.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                     (d.getSources() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)) return true;
        }
        return false;
    }
    private void releaseTouchControls() {
        overlayQueue.removeCallbacksAndMessages(null);
        if (movementStick != null) movementStick.reset();
        for (int i = 0; i < 4; i++) setTouchAxis(i, 0);
        for (int i = 0; i < 16; i++) setTouchButton(i, false);
        if (overlayMenuDown) { SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_ESCAPE); overlayMenuDown = false; }
        aimPointer = -1;
    }
    private boolean aimOrTap(View view, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            aimPointer = event.getPointerId(0); aimStartX = aimLastX = event.getX(); aimStartY = aimLastY = event.getY(); aiming = false;
        } else if (action == MotionEvent.ACTION_MOVE && aimPointer != -1) {
            int i = event.findPointerIndex(aimPointer);
            if (i >= 0) {
                float x = event.getX(i), y = event.getY(i), density = getResources().getDisplayMetrics().density;
                if (Math.hypot(x - aimStartX, y - aimStartY) > 8 * density) aiming = true;
                if (aiming) {
                    setTouchAxis(2, Math.max(-1, Math.min(1, (x - aimLastX) / (20 * density))));
                    setTouchAxis(3, Math.max(-1, Math.min(1, (y - aimLastY) / (20 * density))));
                    overlayQueue.removeCallbacks(stopAim); overlayQueue.postDelayed(stopAim, 90);
                }
                aimLastX = x; aimLastY = y;
            }
        } else if (action == MotionEvent.ACTION_UP && aimPointer != -1) {
            if (!aiming) {
                long now = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, aimStartX, aimStartY, 0);
                MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, event.getX(), event.getY(), 0);
                menuTouch(view, down); menuTouch(view, up); down.recycle(); up.recycle();
            }
            overlayQueue.removeCallbacks(stopAim); stopAim.run(); aimPointer = -1;
        } else if (action == MotionEvent.ACTION_CANCEL) {
            overlayQueue.removeCallbacks(stopAim); stopAim.run(); aimPointer = -1;
        }
        return true;
    }
    private Button overlayButton(String label) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(12);
        b.setTextColor(Color.WHITE); b.setPadding(0, 0, 0, 0);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0x9931475a); bg.setStroke(2, 0xffe4eef8); bg.setCornerRadius(12 * getResources().getDisplayMetrics().density); b.setBackground(bg);
        return b;
    }
    private void addAction(RelativeLayout layout, String label, int index, int right, int bottom, int size, int gap) {
        Button b = overlayButton(label); b.setContentDescription(label);
        b.setOnTouchListener((v,e) -> { int a=e.getActionMasked(); if(a==MotionEvent.ACTION_DOWN) setTouchButton(index,true); else if(a==MotionEvent.ACTION_UP || a==MotionEvent.ACTION_CANCEL) setTouchButton(index,false); return true; });
        RelativeLayout.LayoutParams p=new RelativeLayout.LayoutParams(size,size); p.addRule(RelativeLayout.ALIGN_PARENT_END); p.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); p.setMargins(0,0,gap+right*(size+gap),gap+bottom*(size+gap)); layout.addView(b,p); gameplayViews.add(b);
    }
    private void installTouchLayout(RelativeLayout layout, int size, int margin) {
        float density=getResources().getDisplayMetrics().density;
        movementStick=new TouchStickView(this,(x,y)->{setTouchAxis(0,x);setTouchAxis(1,y);});
        int stickSize=(int)(140*density+.5f);
        RelativeLayout.LayoutParams stick=new RelativeLayout.LayoutParams(stickSize,stickSize); stick.addRule(RelativeLayout.ALIGN_PARENT_START); stick.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); stick.setMargins(margin,0,0,margin); layout.addView(movementStick,stick); gameplayViews.add(movementStick);
        addAction(layout,"Fire",15,1,0,size,margin); addAction(layout,"Alt fire",5,2,0,size,margin);
        addAction(layout,"Jump",0,1,1,size,margin); addAction(layout,"Crouch",1,2,1,size,margin);
        addAction(layout,"Next",2,1,2,size,margin); addAction(layout,"Prev",3,2,2,size,margin);
        addAction(layout,"Dodge",9,3,0,size,margin); addAction(layout,"Trans",10,3,1,size,margin);
        Button menu=overlayButton("Menu"); menu.setContentDescription("Open game menu or skip intro");
        menu.setOnClickListener(v->{ if(!overlayMenuDown){ overlayMenuDown=true; SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_ESCAPE); overlayQueue.postDelayed(()->{if(overlayMenuDown){SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_ESCAPE);overlayMenuDown=false;}},100); }});
        RelativeLayout.LayoutParams mp=new RelativeLayout.LayoutParams(size*2,size); mp.addRule(RelativeLayout.ALIGN_PARENT_TOP); mp.addRule(RelativeLayout.ALIGN_PARENT_END); mp.setMargins(0,margin,margin,0); layout.addView(menu,mp);
        Button toggle=overlayButton("Hide controls"); toggle.setContentDescription("Show or hide touch gameplay controls");
        toggle.setOnClickListener(v->{ boolean visible=gameplayViews.get(0).getVisibility()==View.VISIBLE; releaseTouchControls(); for(View item:gameplayViews)item.setVisibility(visible?View.GONE:View.VISIBLE); toggle.setText(visible?"Show controls":"Hide controls"); });
        // Keep the persistent toggle away from the upper-left Game menu and its dropdown.
        RelativeLayout.LayoutParams tp=new RelativeLayout.LayoutParams(size*2,size); tp.addRule(RelativeLayout.ALIGN_PARENT_TOP); tp.addRule(RelativeLayout.CENTER_HORIZONTAL); tp.setMargins(0,margin,0,0); layout.addView(toggle,tp);
        overlayQueue.postDelayed(()->{ if(!isTouchJoystickReady()) traceInput("touch joystick not opened by engine"); },10000);
    }
    private final HashMap<Integer, boolean[]> triggerStates = new HashMap<>();
    private final HashMap<Integer, Long> lastControllerMotionTrace = new HashMap<>();
    private final HashMap<Integer, Integer> controllerMotionTraceCount = new HashMap<>();
    private final Handler touchQueue = new Handler(Looper.getMainLooper());
    private int activePointer = -1;
    private boolean mouseDown;
    private boolean startMenuDown;
    private long lastTouchEvent;
    private long pressDue;
    private AlertDialog exitDialog;
    private static final long SETTLE_MS = 100;
    private static final long MIN_PRESS_MS = 75;

    private void traceInput(String message) {
        try (FileWriter out = new FileWriter(new File(getFilesDir(), "core-load/input-diagnostic.txt"), true)) {
            out.write(SystemClock.uptimeMillis() + " " + message + "\n");
        } catch (IOException e) { Log.w("UT99Host", "Could not write input diagnostic", e); }
    }

    private void traceControllers() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null) continue;
            int sources = device.getSources();
            if ((sources & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD &&
                    (sources & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) continue;
            traceInput("controller id=" + id + " name=" + device.getName() + " vendor=" + device.getVendorId()
                    + " product=" + device.getProductId() + " sources=0x" + Integer.toHexString(sources));
            for (InputDevice.MotionRange range : device.getMotionRanges()) {
                if ((range.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)
                    traceInput("controller axis id=" + id + " " + MotionEvent.axisToString(range.getAxis())
                            + " min=" + range.getMin() + " max=" + range.getMax() + " flat=" + range.getFlat());
            }
        }
    }

    private void traceDisplayConfig() {
        File config = new File(getFilesDir(), "core-load/UnrealTournament.ini");
        try {
            String section = "";
            for (String line : Files.readAllLines(config.toPath(), StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) section = trimmed;
                if ((section.equalsIgnoreCase("[SDLDrv.SDLClient]") && trimmed.contains("Viewport"))
                        || (section.equalsIgnoreCase("[UMenu.UMenuRootWindow]")
                        && (trimmed.contains("GUIScale") || trimmed.startsWith("LookAndFeelClass=")))) {
                    traceInput("config " + section + " " + trimmed);
                }
            }
        } catch (IOException e) { traceInput("display config unavailable: " + e.getClass().getSimpleName()); }
    }

    private void queueMouse(long due, int button, int action, float x, float y) {
        lastTouchEvent = Math.max(due, lastTouchEvent + 1);
        touchQueue.postAtTime(() -> {
            if (action == MotionEvent.ACTION_DOWN) mouseDown = true;
            if (action == MotionEvent.ACTION_UP) mouseDown = false;
            SDLActivity.onNativeMouse(button, action, x, y, false);
        }, lastTouchEvent);
    }

    private boolean menuTouch(View view, MotionEvent event) {
        int action = event.getActionMasked();
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) {
            activePointer = event.getPointerId(0);
            float x = event.getX(0), y = event.getY(0);
            SDLActivity.onNativeMouse(0, MotionEvent.ACTION_HOVER_MOVE, x, y, false);
            pressDue = Math.max(now + SETTLE_MS, lastTouchEvent + 1);
            queueMouse(pressDue, 1, MotionEvent.ACTION_DOWN, x, y);
        } else if (action == MotionEvent.ACTION_MOVE && activePointer != -1) {
            int i = event.findPointerIndex(activePointer);
            if (i >= 0) queueMouse(now + SETTLE_MS, 1, MotionEvent.ACTION_MOVE, event.getX(i), event.getY(i));
        } else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && activePointer != -1) {
            int i = event.getActionIndex();
            if (event.getPointerId(i) == activePointer) {
                queueMouse(Math.max(now + SETTLE_MS, pressDue + MIN_PRESS_MS), 0, MotionEvent.ACTION_UP, event.getX(i), event.getY(i));
                activePointer = -1;
            }
        } else if (action == MotionEvent.ACTION_CANCEL) {
            cancelTouch();
        }
        return true;
    }

    private void cancelTouch() {
        touchQueue.removeCallbacksAndMessages(null);
        if (mouseDown) SDLActivity.onNativeMouse(0, MotionEvent.ACTION_UP, 0, 0, false);
        mouseDown = false;
        activePointer = -1;
        lastTouchEvent = 0;
    }

    private void installPhoneControls() {
        View content = SDLActivity.getContentView();
        if (!(content instanceof RelativeLayout)) return;
        RelativeLayout layout = (RelativeLayout) content;
        if (layout.getChildCount() == 0) return;
        float density = getResources().getDisplayMetrics().density;
        int size = (int) (56 * density + .5f), margin = (int) (12 * density + .5f);
        Button keyboard = new Button(this);
        keyboard.setText("⌨");
        keyboard.setTextSize(25);
        keyboard.setTextColor(Color.WHITE);
        keyboard.setAllCaps(false);
        keyboard.setContentDescription("Show Android keyboard");
        keyboard.setPadding(0, 0, 0, 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xCC26384A);
        background.setStroke((int) (2 * density + .5f), 0xFFE4EEF8);
        background.setCornerRadius(12 * density);
        keyboard.setBackground(background);
        keyboard.setOnClickListener(v -> {
            if (enableTextInput(getFilesDir().getAbsolutePath() + "/core-load"))
                SDLActivity.showTextInput(0, 0, 1, 1);
        });
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(size, size);
        params.addRule(RelativeLayout.ALIGN_PARENT_END);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        params.setMargins(margin, margin, margin, margin);
        layout.addView(keyboard, params);
        Button controls = new Button(this);
        controls.setText("⚙"); controls.setTextSize(24); controls.setTextColor(Color.WHITE);
        controls.setContentDescription("Controller settings"); controls.setPadding(0, 0, 0, 0);
        controls.setBackground(background.getConstantState().newDrawable().mutate());
        controls.setOnClickListener(v -> showControllerSettings());
        RelativeLayout.LayoutParams controlsParams = new RelativeLayout.LayoutParams(size, size);
        controlsParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        controlsParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        controlsParams.setMargins(margin, margin, margin, margin * 2 + size);
        layout.addView(controls, controlsParams);
        if (touchMode) installTouchLayout(layout, size, margin);
    }

    private void showControllerSettings() {
        if (!isCameraSensitivityReady()) {
            new AlertDialog.Builder(this).setTitle("Controller settings")
                    .setMessage("Camera sensitivity is unavailable for this runtime.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        android.content.SharedPreferences prefs = getSharedPreferences("phone-controller", MODE_PRIVATE);
        int initial = Math.max(25, Math.min(400, prefs.getInt("camera-sensitivity-percent", 100)));
        int padding = (int) (24 * getResources().getDisplayMetrics().density + .5f);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL); content.setPadding(padding, padding / 2, padding, 0);
        TextView label = new TextView(this);
        label.setTextSize(16); label.setText("Joystick camera sensitivity: " + initial + "%");
        content.addView(label);
        SeekBar slider = new SeekBar(this);
        slider.setMax(375); slider.setProgress(initial - 25); content.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int percent = progress + 25;
                label.setText("Joystick camera sensitivity: " + percent + "%");
                if (fromUser) {
                    setCameraSensitivity(percent / 100f);
                    prefs.edit().putInt("camera-sensitivity-percent", percent).apply();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        new AlertDialog.Builder(this).setTitle("Controller settings").setView(content)
                .setPositiveButton("Done", null).show();
    }

    @Override protected SDLSurface createSDLSurface(Context context) {
        return new SDLSurface(context) {
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                super.surfaceChanged(holder, format, width, height);
                traceInput("surface=" + width + "x" + height + " format=" + format);
            }
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) return touchMode ? aimOrTap(view, event) : menuTouch(view, event);
                return super.onTouch(view, event);
            }
        };
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        InputDevice device = event.getDevice();
        int sources = event.getSource() | (device != null ? device.getSources() : 0);
        boolean controller = (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        // UT's joystick-bound ShowMenu alone can pause without showing the
        // UWindow menu. Deliver the original Escape UI event on Start, while
        // still forwarding the physical button as a joystick event below.
        if (controller && event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_START && event.getRepeatCount() == 0) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && !startMenuDown) {
                startMenuDown = true;
                SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_ESCAPE);
            } else if (event.getAction() == KeyEvent.ACTION_UP && startMenuDown) {
                startMenuDown = false;
                SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_ESCAPE);
            }
        }
        if (controller && (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_L2
                || event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_R2)) {
            int side = event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_L2 ? 0 : 1;
            int axis = triggerAxis(device, side);
            if (axis < 0 && (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP))
                setTrigger(event.getDeviceId(), side, event.getAction() == KeyEvent.ACTION_DOWN);
            return true;
        }
        if (!controller && event.getKeyCode() == KeyEvent.KEYCODE_BACK && !isKeyboardVisible()) {
            if (event.getAction() == KeyEvent.ACTION_UP && event.getRepeatCount() == 0) confirmExit();
            return true;
        }
        if (controller && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() > 0) return true;
        if (event.getRepeatCount() == 0 && (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_START || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE)) {
            traceInput("key=" + event.getKeyCode() + " action=" + event.getAction()
                    + " repeat=" + event.getRepeatCount() + " source=0x" + Integer.toHexString(event.getSource())
                    + " deviceSources=0x" + Integer.toHexString(device != null ? device.getSources() : 0)
                    + " controller=" + controller);
        }
        if (controller && event.getRepeatCount() == 0
                && (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP)) {
            int handled = event.getAction() == KeyEvent.ACTION_DOWN
                    ? SDLControllerManager.onNativePadDown(event.getDeviceId(), event.getKeyCode())
                    : SDLControllerManager.onNativePadUp(event.getDeviceId(), event.getKeyCode());
            traceInput("controller button id=" + event.getDeviceId() + " key="
                    + KeyEvent.keyCodeToString(event.getKeyCode()) + " action=" + event.getAction() + " sdl=" + handled);
            // Gamepad keys must never fall through to SDL's keyboard handler.
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private int triggerAxis(InputDevice device, int side) {
        if (device == null) return -1;
        int primary = side == 0 ? MotionEvent.AXIS_LTRIGGER : MotionEvent.AXIS_RTRIGGER;
        int fallback = side == 0 ? MotionEvent.AXIS_BRAKE : MotionEvent.AXIS_GAS;
        if (device.getMotionRange(primary, InputDevice.SOURCE_JOYSTICK) != null) return primary;
        return device.getMotionRange(fallback, InputDevice.SOURCE_JOYSTICK) != null ? fallback : -1;
    }

    private void setTrigger(int id, int side, boolean pressed) {
        boolean[] state = triggerStates.computeIfAbsent(id, key -> new boolean[2]);
        if (state[side] == pressed) return;
        state[side] = pressed;
        int key = side == 0 ? KeyEvent.KEYCODE_BUTTON_L2 : KeyEvent.KEYCODE_BUTTON_R2;
        traceInput("trigger=" + (side == 0 ? "left" : "right") + " pressed=" + pressed);
        if (pressed) SDLControllerManager.onNativePadDown(id, key);
        else SDLControllerManager.onNativePadUp(id, key);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            InputDevice device = event.getDevice();
            long now = SystemClock.uptimeMillis();
            Long previous = lastControllerMotionTrace.get(event.getDeviceId());
            int count = controllerMotionTraceCount.getOrDefault(event.getDeviceId(), 0);
            if (count < 60 && (previous == null || now - previous >= 1000)) {
                StringBuilder values = new StringBuilder("controller motion id=").append(event.getDeviceId());
                if (device != null) for (InputDevice.MotionRange range : device.getMotionRanges()) {
                    if ((range.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)
                        values.append(' ').append(MotionEvent.axisToString(range.getAxis())).append('=').append(event.getAxisValue(range.getAxis()));
                }
                traceInput(values.toString());
                lastControllerMotionTrace.put(event.getDeviceId(), now);
                controllerMotionTraceCount.put(event.getDeviceId(), count + 1);
            }
            for (int side = 0; side < 2; ++side) {
                int axis = triggerAxis(device, side);
                if (axis < 0) continue;
                boolean[] state = triggerStates.get(event.getDeviceId());
                boolean held = state != null && state[side];
                float value = event.getAxisValue(axis);
                setTrigger(event.getDeviceId(), side, value >= (held ? .30f : .45f));
            }
            // Route joystick axes explicitly; Android otherwise sends generic
            // motion to whichever view has focus (such as the keyboard button).
            return SDLControllerManager.handleJoystickMotionEvent(event);
        }
        return super.dispatchGenericMotionEvent(event);
    }

    private boolean isKeyboardVisible() {
        WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
        return insets != null && insets.isVisible(WindowInsets.Type.ime());
    }

    private void confirmExit() {
        if (isFinishing() || isDestroyed() || (exitDialog != null && exitDialog.isShowing())) return;
        exitDialog = new AlertDialog.Builder(this).setTitle("Exit UT99?")
                .setMessage("Leave the game and return to setup?")
                .setNegativeButton("Keep playing", (dialog, which) -> {})
                .setPositiveButton("Exit", (dialog, which) -> finish()).create();
        exitDialog.show();
    }

    @Override public void onBackPressed() { confirmExit(); }

    @Override protected void onDestroy() {
        if (deviceListener != null) ((android.hardware.input.InputManager) getSystemService(INPUT_SERVICE)).unregisterInputDeviceListener(deviceListener);
        GameSession.finish(this);
        super.onDestroy();
    }

    @Override protected void onPause() {
        cancelTouch();
        if (touchMode) releaseTouchControls();
        if (startMenuDown) {
            startMenuDown = false;
            SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_ESCAPE);
        }
        for (java.util.Map.Entry<Integer, boolean[]> entry : triggerStates.entrySet()) {
            if (entry.getValue()[0]) SDLControllerManager.onNativePadUp(entry.getKey(), KeyEvent.KEYCODE_BUTTON_L2);
            if (entry.getValue()[1]) SDLControllerManager.onNativePadUp(entry.getKey(), KeyEvent.KEYCODE_BUTTON_R2);
        }
        triggerStates.clear();
        lastControllerMotionTrace.clear();
        controllerMotionTraceCount.clear();
        super.onPause();
    }
    @Override public void setRequestedOrientation(int requestedOrientation) {
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }
    @Override protected void onCreate(Bundle state) {
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) { finish(); return; }
        touchMode = !hasPhysicalController();
        GameSession.start(this);
        CrashReporter.installGameExceptionHandler(this);
        PhoneDisplayConfig.apply(this);
        File trace = new File(getFilesDir(), "core-load/input-diagnostic.txt");
        if (trace.exists() && !trace.delete()) Log.w("UT99Host", "Could not reset input diagnostic");
        android.graphics.Rect bounds = getWindowManager().getMaximumWindowMetrics().getBounds();
        traceInput("maximum window=" + bounds.width() + "x" + bounds.height());
        traceDisplayConfig();
        traceControllers();
        super.onCreate(state);
        int percent = Math.max(25, Math.min(400, getSharedPreferences("phone-controller", MODE_PRIVATE)
                .getInt("camera-sensitivity-percent", 100)));
        setCameraSensitivity(percent / 100f);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::confirmExit);
        }
        installPhoneControls();
        deviceListener = new android.hardware.input.InputManager.InputDeviceListener() {
            private boolean hadController = !touchMode;
            private void changed() {
                boolean connected = hasPhysicalController();
                if (connected != hadController) {
                    hadController = connected;
                    android.widget.Toast.makeText(ExperimentalSDLActivity.this,
                            "Controller connection changed. Restart the game to switch controls.", android.widget.Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onInputDeviceAdded(int id) { changed(); }
            @Override public void onInputDeviceRemoved(int id) { changed(); }
            @Override public void onInputDeviceChanged(int id) { changed(); }
        };
        ((android.hardware.input.InputManager) getSystemService(INPUT_SERVICE)).registerInputDeviceListener(deviceListener, overlayQueue);
    }
    @Override public void loadLibraries() {
        String path = getFilesDir().getAbsolutePath() + "/core-load/libSDL2-2.0.so.0";
        Log.i("UT99Host", "Loading diagnostic SDL Java bridge: " + path);
        try {
            System.load(path);
            System.loadLibrary("ut99host");
            if (BuildConfig.DIRECT_REHOST) DirectMemoryProbe.registerAdaptations(getFilesDir().getAbsolutePath());
        } catch (UnsatisfiedLinkError e) {
            CrashReporter.recordGameFailure(this, "Native library loading failed", e);
            throw e;
        } catch (Exception e) {
            CrashReporter.recordGameFailure(this, "In-memory compatibility setup failed", e);
            throw new IllegalStateException("Could not prepare the in-memory compatibility layer", e);
        }
    }
    @Override protected String getMainSharedObject() {
        return getApplicationInfo().nativeLibraryDir + "/libut99host.so";
    }
    @Override protected String getMainFunction() { return "ut_sdl_experiment_main"; }
    @Override protected String[] getArguments() {
        String dir = getFilesDir().getAbsolutePath() + "/core-load";
        if ("gl_probe".equals(getIntent().getStringExtra("mode"))) return new String[]{dir, "gl_probe"};
        String server = getIntent().getStringExtra("server-address");
        if (server != null) {
            try { return new String[]{dir, touchMode ? "touch" : "controller", ServerAddress.parse(server)}; }
            catch (IllegalArgumentException e) { Log.e("UT99Host", "Rejected invalid server address", e); }
        }
        return touchMode ? new String[]{dir, "touch"} : new String[]{dir};
    }
}
