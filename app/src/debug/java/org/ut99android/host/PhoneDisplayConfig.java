package org.ut99android.host;

import android.app.Activity;
import android.graphics.Rect;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Configures the original engine's viewport, not the Android surface resolution. */
final class PhoneDisplayConfig {
    private PhoneDisplayConfig() {}

    static void apply(Activity activity) {
        Rect bounds = activity.getWindowManager().getMaximumWindowMetrics().getBounds();
        int width = Math.max(bounds.width(), bounds.height());
        int height = Math.min(bounds.width(), bounds.height());
        if (width <= 0 || height <= 0) return;
        File ini = new File(activity.getFilesDir(), "core-load/UnrealTournament.ini");
        if (!ini.isFile()) return;
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(ini.toPath(), StandardCharsets.UTF_8));
            if (BuildConfig.DIRECT_REHOST) set(lines, "Engine.Engine", "ViewportManager", "SDLDrv.SDLClient");
            boolean hasCluster = false;
            for (String[] entry : RuntimeCatalog.FILES)
                if (entry[0].equals("Cluster.so")) hasCluster = true;
            if (!hasCluster && get(lines, "Engine.Engine", "AudioDevice").equalsIgnoreCase("Cluster.ClusterAudioSubsystem")) {
                File backup = new File(ini.getParentFile(), "UnrealTournament.ini.before-openal-only");
                if (!backup.exists()) Files.copy(ini.toPath(), backup.toPath());
                set(lines, "Engine.Engine", "AudioDevice", "ALAudio.ALAudioSubsystem");
                Log.i("UT99Host", "Switched unavailable Cluster audio to ALAudio; previous configuration backed up");
            }
            set(lines, "SDLDrv.SDLClient", "WindowedViewportX", Integer.toString(width));
            set(lines, "SDLDrv.SDLClient", "WindowedViewportY", Integer.toString(height));
            set(lines, "SDLDrv.SDLClient", "FullscreenViewportX", Integer.toString(width));
            set(lines, "SDLDrv.SDLClient", "FullscreenViewportY", Integer.toString(height));
            set(lines, "SDLDrv.SDLClient", "UseJoystick", "True");
            // Keep at least a 640x360 logical workspace for the original desktop menus.
            // 469e's settings expose GUI scale in the 1x-3x range.
            float scale = Math.max(1.0f, Math.min(3.0f,
                    Math.min(width / 640.0f, height / 360.0f)));
            // The bundled UWindow font table has entries at quarter steps.
            scale = (float) Math.floor(scale * 4.0f) / 4.0f;
            String guiScale = String.format(Locale.ROOT, "%.2f", scale);
            // Pick a usable initial scale, but retain later changes made in UT.
            if (get(lines, "UMenu.UMenuRootWindow", "ConfiguredGUIScale").isEmpty()) {
                set(lines, "UMenu.UMenuRootWindow", "AutoGUIScale", "False");
                set(lines, "UMenu.UMenuRootWindow", "ConfiguredGUIScale", guiScale);
                set(lines, "UMenu.UMenuRootWindow", "GUIScale", guiScale);
            }
            // A stale or invalid style class leaves the entire UWindow menu invisible.
            // Use the stock blue style, which is present in the supported UT menu package.
            if (get(lines, "UMenu.UMenuRootWindow", "LookAndFeelClass").isEmpty())
                set(lines, "UMenu.UMenuRootWindow", "LookAndFeelClass", "UMenu.UMenuBlueLookAndFeel");
            // The game runs in another process, whose SharedPreferences cache
            // can be stale after a re-import. Carry the validated import ID
            // explicitly on each launch.
            File gameRoot = GameFolderImporter.fromId(activity, activity.getIntent().getStringExtra("import-id"));
            if (gameRoot == null) gameRoot = GameFolderImporter.active(activity);
            if (gameRoot != null) {
                String root = gameRoot.getAbsolutePath();
                replacePaths(lines, root);
                Log.i("UT99Host", "Reading game assets directly from " + root);
            }
            File temp = new File(ini.getParentFile(), "UnrealTournament.ini.tmp");
            Files.write(temp.toPath(), lines, StandardCharsets.UTF_8);
            Files.move(temp.toPath(), ini.toPath(), StandardCopyOption.REPLACE_EXISTING);
            initializeControllerDefaults(activity);
            migrateControllerButtons(activity);
            migrateTriggerSlots(activity);
            migrateKnownForwardBinding(activity);
            applyRequestedControllerLayout(activity);
            applySouthEastLayout(activity);
            applyShoulderLayout(activity);
            applyDedicatedDodgeLayout(activity);
            Log.i("UT99Host", String.format(Locale.ROOT, "Requested native viewport %dx%d; GUI scale %s", width, height, guiScale));
        } catch (IOException e) {
            Log.e("UT99Host", "Could not update the experimental viewport configuration", e);
        }
    }

    private static void applyDedicatedDodgeLayout(Activity activity) throws IOException {
        android.content.SharedPreferences prefs = activity.getSharedPreferences("phone-controller", Activity.MODE_PRIVATE);
        if (prefs.getBoolean("dedicated-dodge-layout-v8", false)) return;
        File user = new File(activity.getFilesDir(), "core-load/User.ini");
        if (!user.isFile()) return;
        File backup = new File(user.getParentFile(), "User.ini.before-dedicated-dodge-v8");
        if (!backup.exists()) Files.copy(user.toPath(), backup.toPath());
        List<String> lines = new ArrayList<>(Files.readAllLines(user.toPath(), StandardCharsets.UTF_8));
        String east = get(lines, "Engine.Input", "Joy2"), north = get(lines, "Engine.Input", "Joy4");
        set(lines, "Engine.Input", "Joy2", north);
        set(lines, "Engine.Input", "Joy4", east);
        set(lines, "Engine.Input", "Joy12", "ShowObjectives");
        set(lines, "Engine.Input", "Joy13", "Feigndeath");
        set(lines, "Engine.Input", "Joy14", "Taunt Taunt1");
        set(lines, "Engine.Input", "Joy15", "Taunt Victory1");
        set(lines, "Engine.Input", "JoyPovUp", "ShowObjectives");
        set(lines, "Engine.Input", "JoyPovDown", "Feigndeath");
        set(lines, "Engine.Input", "JoyPovLeft", "Taunt Taunt1");
        set(lines, "Engine.Input", "JoyPovRight", "Taunt Victory1");
        // Normal movement bypasses the game's double-tap detector. Joy8 is
        // an internal always-held strafe modifier; JoyU/V carry dodge pulses.
        set(lines, "Engine.Input", "JoyX", "Axis aBaseX Speed=+300.0");
        set(lines, "Engine.Input", "JoyY", "Axis aForward Speed=-300.0");
        set(lines, "Engine.Input", "Joy8", "Button bStrafe");
        set(lines, "Engine.Input", "JoyU", "Axis aStrafe Speed=+300.0");
        set(lines, "Engine.Input", "JoyV", "Axis aBaseY Speed=-300.0");
        File temp = new File(user.getParentFile(), "User.ini.controller.tmp");
        Files.write(temp.toPath(), lines, StandardCharsets.UTF_8);
        Files.move(temp.toPath(), user.toPath(), StandardCopyOption.REPLACE_EXISTING);
        prefs.edit().putBoolean("dedicated-dodge-layout-v8", true).commit();
    }

    private static void applyShoulderLayout(Activity activity) throws IOException {
        android.content.SharedPreferences prefs = activity.getSharedPreferences("phone-controller", Activity.MODE_PRIVATE);
        if (prefs.getBoolean("shoulder-layout-v7", false)) return;
        File user = new File(activity.getFilesDir(), "core-load/User.ini");
        if (!user.isFile()) return;
        File backup = new File(user.getParentFile(), "User.ini.before-shoulder-layout-v7");
        if (!backup.exists()) Files.copy(user.toPath(), backup.toPath());
        List<String> lines = new ArrayList<>(Files.readAllLines(user.toPath(), StandardCharsets.UTF_8));
        set(lines, "Engine.Input", "Joy10", ""); // LB is handled by the host's directional dodge.
        set(lines, "Engine.Input", "Joy11", "GetWeapon BotPack.Translocator");
        File temp = new File(user.getParentFile(), "User.ini.controller.tmp");
        Files.write(temp.toPath(), lines, StandardCharsets.UTF_8);
        Files.move(temp.toPath(), user.toPath(), StandardCopyOption.REPLACE_EXISTING);
        prefs.edit().putBoolean("shoulder-layout-v7", true).commit();
    }

    private static void applySouthEastLayout(Activity activity) throws IOException {
        android.content.SharedPreferences prefs = activity.getSharedPreferences("phone-controller", Activity.MODE_PRIVATE);
        if (prefs.getBoolean("standard-layout-v6", false)) return;
        File user = new File(activity.getFilesDir(), "core-load/User.ini");
        if (!user.isFile()) return;
        File backup = new File(user.getParentFile(), "User.ini.before-standard-layout-v6");
        if (!backup.exists()) Files.copy(user.toPath(), backup.toPath());
        List<String> lines = new ArrayList<>(Files.readAllLines(user.toPath(), StandardCharsets.UTF_8));
        set(lines, "Engine.Input", "Joy2", "Duck");
        File temp = new File(user.getParentFile(), "User.ini.controller.tmp");
        Files.write(temp.toPath(), lines, StandardCharsets.UTF_8);
        Files.move(temp.toPath(), user.toPath(), StandardCopyOption.REPLACE_EXISTING);
        prefs.edit().putBoolean("standard-layout-v6", true).commit();
    }

    // Apply the requested Xbox layout once. Later in-game remapping remains
    // under the player's control; keep a separate snapshot of prior bindings.
    private static void applyRequestedControllerLayout(Activity activity) throws IOException {
        android.content.SharedPreferences prefs = activity.getSharedPreferences("phone-controller", Activity.MODE_PRIVATE);
        if (prefs.getBoolean("xbox-layout-v5", false)) return;
        File user = new File(activity.getFilesDir(), "core-load/User.ini");
        if (!user.isFile()) return;
        File backup = new File(user.getParentFile(), "User.ini.before-xbox-layout-v5");
        if (!backup.exists()) Files.copy(user.toPath(), backup.toPath());
        List<String> lines = new ArrayList<>(Files.readAllLines(user.toPath(), StandardCharsets.UTF_8));
        set(lines, "Engine.Input", "Joy1", "Jump");
        set(lines, "Engine.Input", "Joy5", "ShowScores");
        set(lines, "Engine.Input", "Joy6", "AltFire");
        // The host routes Start to the same UI event as Escape; leaving its
        // joystick binding empty prevents a second ShowMenu toggle.
        set(lines, "Engine.Input", "Joy7", "");
        set(lines, "Engine.Input", "Joy8", ""); // Reserved for stick-click dodge; implementation pending.
        set(lines, "Engine.Input", "Joy9", "GetWeapon BotPack.Translocator");
        set(lines, "Engine.Input", "Joy10", "Duck");
        set(lines, "Engine.Input", "Joy11", "Jump");
        set(lines, "Engine.Input", "Joy16", "Fire");
        // These slots can carry Xbox trigger axes; gameplay uses the two
        // joystick trigger buttons instead of treating them as camera axes.
        set(lines, "Engine.Input", "JoyU", "");
        set(lines, "Engine.Input", "JoyV", "");
        File temp = new File(user.getParentFile(), "User.ini.controller.tmp");
        Files.write(temp.toPath(), lines, StandardCharsets.UTF_8);
        Files.move(temp.toPath(), user.toPath(), StandardCopyOption.REPLACE_EXISTING);
        prefs.edit().putBoolean("xbox-layout-v5", true).commit();
    }

    // Correct the previously recommended binding once, without changing other
    // user-selected axes or buttons. The negative binding is already correct.
    private static void migrateKnownForwardBinding(Activity activity) throws IOException {
        android.content.SharedPreferences prefs = activity.getSharedPreferences("phone-controller", Activity.MODE_PRIVATE);
        if (prefs.getBoolean("forward-binding-v4", false)) return;
        File user = new File(activity.getFilesDir(), "core-load/User.ini");
        if (!user.isFile()) return;
        List<String> lines = new ArrayList<>(Files.readAllLines(user.toPath(), StandardCharsets.UTF_8));
        String current = get(lines, "Engine.Input", "JoyY");
        if (current.matches("(?i)\\s*Axis\\s+aBaseY\\s+Speed\\s*=\\s*\\+?300(?:\\.0+)?\\s*")) {
            File backup = new File(user.getParentFile(), "User.ini.before-forward-fix");
            if (!backup.exists()) Files.copy(user.toPath(), backup.toPath());
            set(lines, "Engine.Input", "JoyY", "Axis aBaseY Speed=-300.0");
            File temp = new File(user.getParentFile(), "User.ini.controller.tmp");
            Files.write(temp.toPath(), lines, StandardCharsets.UTF_8);
            Files.move(temp.toPath(), user.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        prefs.edit().putBoolean("forward-binding-v4", true).apply();
    }

    private static void migrateTriggerSlots(Activity activity) throws IOException {
        android.content.SharedPreferences prefs = activity.getSharedPreferences("phone-controller", Activity.MODE_PRIVATE);
        if (prefs.getBoolean("trigger-slots-v3", false)) return;
        File user = new File(activity.getFilesDir(), "core-load/User.ini");
        if (!user.isFile()) return;
        List<String> lines = new ArrayList<>(Files.readAllLines(user.toPath(), StandardCharsets.UTF_8));
        String left = get(lines, "Engine.Input", "Joy16");
        String right = get(lines, "Engine.Input", "UnknownD8");
        String slot6 = get(lines, "Engine.Input", "Joy6");
        File backup = new File(user.getParentFile(), "User.ini.before-android-controller");
        List<String> prior = backup.isFile() ? Files.readAllLines(backup.toPath(), StandardCharsets.UTF_8) : new ArrayList<>();
        String originalSlot6 = get(prior, "Engine.Input", "Joy6");
        if (left.equalsIgnoreCase("AltFire") && right.equalsIgnoreCase("Fire")
                && (slot6.isEmpty() || (backup.isFile() && slot6.equalsIgnoreCase(originalSlot6)))) {
            set(lines, "Engine.Input", "Joy6", "AltFire");
            set(lines, "Engine.Input", "Joy16", "Fire");
            set(lines, "Engine.Input", "UnknownD8", "");
        }
        File temp = new File(user.getParentFile(), "User.ini.controller.tmp");
        Files.write(temp.toPath(), lines, StandardCharsets.UTF_8);
        Files.move(temp.toPath(), user.toPath(), StandardCopyOption.REPLACE_EXISTING);
        prefs.edit().putBoolean("trigger-slots-v3", true).apply();
    }

    private static String get(List<String> lines, String section, String key) {
        boolean inside = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inside = trimmed.substring(1, trimmed.length() - 1).equalsIgnoreCase(section);
            } else if (inside) {
                int pos = line.indexOf('=');
                if (pos >= 0 && line.substring(0, pos).trim().equalsIgnoreCase(key)) return line.substring(pos + 1).trim();
            }
        }
        return "";
    }

    private static void migrateControllerButtons(Activity activity) throws IOException {
        android.content.SharedPreferences prefs = activity.getSharedPreferences("phone-controller", Activity.MODE_PRIVATE);
        if (prefs.getBoolean("native-buttons-v2", false)) return;
        File user = new File(activity.getFilesDir(), "core-load/User.ini");
        if (!user.isFile()) return;
        List<String> lines = new ArrayList<>(Files.readAllLines(user.toPath(), StandardCharsets.UTF_8));
        File backup = new File(user.getParentFile(), "User.ini.before-android-controller");
        List<String> prior = backup.isFile() ? Files.readAllLines(backup.toPath(), StandardCharsets.UTF_8) : new ArrayList<>();
        if (get(lines, "Engine.Input", "Joy16").isEmpty()) set(lines, "Engine.Input", "Joy16", "AltFire");
        if (get(lines, "Engine.Input", "UnknownD8").isEmpty()) set(lines, "Engine.Input", "UnknownD8", "Fire");
        if (get(lines, "Engine.Input", "PageUp").equalsIgnoreCase("Fire"))
            set(lines, "Engine.Input", "PageUp", get(prior, "Engine.Input", "PageUp"));
        if (get(lines, "Engine.Input", "Insert").equalsIgnoreCase("AltFire"))
            set(lines, "Engine.Input", "Insert", get(prior, "Engine.Input", "Insert"));
        File temp = new File(user.getParentFile(), "User.ini.controller.tmp");
        Files.write(temp.toPath(), lines, StandardCharsets.UTF_8);
        Files.move(temp.toPath(), user.toPath(), StandardCopyOption.REPLACE_EXISTING);
        prefs.edit().putBoolean("native-buttons-v2", true).apply();
    }

    private static void initializeControllerDefaults(Activity activity) throws IOException {
        android.content.SharedPreferences prefs = activity.getSharedPreferences("phone-controller", Activity.MODE_PRIVATE);
        if (prefs.getBoolean("default-bindings-v1", false)) return;
        File user = new File(activity.getFilesDir(), "core-load/User.ini");
        if (!user.isFile()) return;
        File backup = new File(user.getParentFile(), "User.ini.before-android-controller");
        if (!backup.exists()) Files.copy(user.toPath(), backup.toPath());
        List<String> lines = new ArrayList<>(Files.readAllLines(user.toPath(), StandardCharsets.UTF_8));
        // Android SDL normally exposes the two right-stick axes as axes 2 and 3.
        set(lines, "Engine.Input", "JoyX", "Axis aStrafe Speed=+300.0");
        set(lines, "Engine.Input", "JoyY", "Axis aBaseY Speed=-300.0");
        set(lines, "Engine.Input", "JoyZ", "Axis aTurn Speed=+150.0");
        set(lines, "Engine.Input", "JoyR", "Axis aLookUp Speed=-100.0");
        set(lines, "Engine.Input", "JoyU", "");
        set(lines, "Engine.Input", "JoyV", "");
        set(lines, "Engine.Input", "Joy1", "Jump");
        set(lines, "Engine.Input", "Joy2", "Duck");
        set(lines, "Engine.Input", "Joy3", "NextWeapon");
        set(lines, "Engine.Input", "Joy4", "PrevWeapon");
        set(lines, "Engine.Input", "Joy5", "");
        set(lines, "Engine.Input", "Joy7", "");
        set(lines, "Engine.Input", "Joy10", "PrevWeapon");
        set(lines, "Engine.Input", "Joy11", "NextWeapon");
        File temp = new File(user.getParentFile(), "User.ini.controller.tmp");
        Files.write(temp.toPath(), lines, StandardCharsets.UTF_8);
        Files.move(temp.toPath(), user.toPath(), StandardCopyOption.REPLACE_EXISTING);
        prefs.edit().putBoolean("default-bindings-v1", true).apply();
    }

    static void replacePaths(List<String> lines, String root) {
        int start = -1, end = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("[") && line.endsWith("]")) {
                if (start >= 0) { end = i; break; }
                if (line.equalsIgnoreCase("[Core.System]")) start = i;
            }
        }
        if (start < 0) { lines.add("[Core.System]"); start = lines.size() - 1; end = lines.size(); }
        for (int i = end - 1; i > start; i--) {
            String line = lines.get(i).trim();
            int pos = line.indexOf('=');
            if (pos >= 0 && (line.substring(0, pos).trim().equalsIgnoreCase("Paths")
                    || line.substring(0, pos).trim().equalsIgnoreCase("LangPaths"))) lines.remove(i);
        }
        String[] paths = {"System/*.u", "Maps/*.unr", "Textures/*.utx", "Sounds/*.uax", "Music/*.umx"};
        int insert = start + 1;
        for (String path : paths) lines.add(insert++, "Paths=" + root + "/" + path);
        lines.add(insert++, "LangPaths=" + root + "/SystemLocalized/<lang>/*.<lang>");
        lines.add(insert, "LangPaths=" + root + "/System/*.<lang>");
    }

    static void set(List<String> lines, String section, String key, String value) {
        int start = -1;
        int end = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("[") && line.endsWith("]")) {
                if (start >= 0) { end = i; break; }
                if (line.substring(1, line.length() - 1).equalsIgnoreCase(section)) start = i;
            }
        }
        if (start < 0) { lines.add("[" + section + "]"); lines.add(key + "=" + value); return; }
        for (int i = start + 1; i < end; i++) {
            String line = lines.get(i);
            int equals = line.indexOf('=');
            if (equals >= 0 && line.substring(0, equals).trim().equalsIgnoreCase(key)) {
                lines.set(i, key + "=" + value); return;
            }
        }
        lines.add(end, key + "=" + value);
    }
}
