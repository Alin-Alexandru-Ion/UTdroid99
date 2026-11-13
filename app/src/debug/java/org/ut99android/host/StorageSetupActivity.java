package org.ut99android.host;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Imports the user's chosen folder into private storage for the native engine. */
public final class StorageSetupActivity extends Activity {
    private static final int PICK_FOLDER = 101, EXPORT_REPORTS = 102, PICK_CONTENT = 103, PICK_PATCH = 104;
    private TextView status, readiness;
    private Button browse, cancel, start, join, report, patchButton;
    private Button done;
    private ProgressBar progressBar;
    private FrameLayout frame;
    private ScrollView pageView;
    private static final int PATCH_PAGE = 0, GAME_PAGE = 1, HOME_PAGE = 2;
    private int page;
    private String workText = "";
    private long workDone = -1, workTotal = -1;
    private boolean busy, importing;
    private int pendingCategory = -1;
    private final AtomicBoolean cancelImport = new AtomicBoolean();
    private String notice = "";

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable panel(int color, int border) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{color, 0xff14191c}); d.setCornerRadius(dp(4));
        d.setStroke(dp(1), border); return d;
    }
    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private Button button(String text, boolean primary) {
        Button view = new Button(this); view.setText(text); view.setTextSize(15); view.setAllCaps(false); view.setTextColor(0xfff5eee3);
        view.setTypeface(Typeface.create("sans-serif-condensed-medium", Typeface.NORMAL));
        view.setBackground(panel(primary ? 0xff9c3b2c : 0xff363e42, primary ? 0xffd77b57 : 0xff707b80));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(52)); p.setMargins(0, dp(6), 0, 0); view.setLayoutParams(p); return view;
    }
    private void heading(LinearLayout parent, String title, String subtitle) {
        TextView heading = label(title, 16, 0xfff6f1e8); heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setPadding(0, dp(20), 0, dp(3)); parent.addView(heading);
        TextView detail = label(subtitle, 14, 0xffafbdc9); detail.setLineSpacing(dp(3), 1f); parent.addView(detail);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().setStatusBarColor(0xff101922); getWindow().setNavigationBarColor(0xff101922);
        frame = new FrameLayout(this); frame.setBackground(new ForgedSteelDrawable());
        setContentView(frame);
        android.content.SharedPreferences wizard = getSharedPreferences("setup-pages", MODE_PRIVATE);
        if (!wizard.contains("initialized")) {
            boolean patchExists = patchReady();
            boolean gameExists = problem(GameFolderImporter.active(this)) == null;
            wizard.edit().putBoolean("initialized", true).putBoolean("patch-confirmed", patchExists)
                    .putBoolean("game-confirmed", gameExists).commit();
        }
        page = initialPage();
        if (state != null) { pendingCategory = state.getInt("content-category", -1); page = state.getInt("setup-page", page); }
        renderPage();
        boolean acknowledged = !BuildConfig.ENGINE_FREE_EXPERIMENT || getSharedPreferences("setup-acknowledgment", MODE_PRIVATE)
                .getBoolean("compatibility-v1", false);
        if (state == null) showAttribution(frame, () -> {
            if (isFinishing() || isDestroyed()) return;
            if (!acknowledged) showCompatibilityAcknowledgment(frame);
            else if (getIntent().getBooleanExtra("verify_runtime", false)) prepare(false);
        });
        else if (!acknowledged) showCompatibilityAcknowledgment(frame);
        else if (getIntent().getBooleanExtra("verify_runtime", false)) prepare(false);
    }
    private boolean patchReady() {
        if (!BuildConfig.ENGINE_FREE_EXPERIMENT) return true;
        try { return PatchImportBridge.backend().active(this) != null; } catch (IOException e) { return false; }
    }
    private int initialPage() {
        android.content.SharedPreferences prefs = getSharedPreferences("setup-pages", MODE_PRIVATE);
        if (!patchReady() || !prefs.getBoolean("patch-confirmed", false)) return PATCH_PAGE;
        if (problem(GameFolderImporter.active(this)) != null || !prefs.getBoolean("game-confirmed", false)) return GAME_PAGE;
        return HOME_PAGE;
    }
    private void changePage(int next) { page = next; notice = ""; workText = ""; renderPage(); }
    private void renderPage() {
        if (pageView != null) frame.removeView(pageView);
        status = readiness = null; browse = cancel = start = join = report = patchButton = done = null; progressBar = null;
        ScrollView scroll = new ScrollView(this); pageView = scroll; scroll.setFillViewport(true);
        LinearLayout outer = new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL); outer.setGravity(Gravity.CENTER_HORIZONTAL);
        outer.setPadding(dp(20), dp(20), dp(20), dp(32));
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        outer.addView(content, new LinearLayout.LayoutParams(Math.min(dp(620), getResources().getDisplayMetrics().widthPixels - dp(40)), -2));
        TextView title = label("UTDROID99", 36, 0xfff6f1e8); title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)); title.setLetterSpacing(.09f); content.addView(title);
        TextView subtitle = label(page == HOME_PAGE ? "UNREAL TOURNAMENT  /  ANDROID" : "GETTING READY  /  " + (page == PATCH_PAGE ? "PATCH" : "GAME FILES"), 12, 0xffdc9b79);
        subtitle.setLetterSpacing(.12f); subtitle.setPadding(0, dp(3), 0, dp(18)); content.addView(subtitle);
        readiness = label("", 15, 0xfff6f1e8); readiness.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        readiness.setPadding(dp(14), dp(12), dp(14), dp(12)); content.addView(readiness);
        if (page == PATCH_PAGE) {
            heading(content, "Get the OldUnreal patch", "Download the Linux ARM64 archive from the 469e release page. Choose the file ending in Linux-arm64.tar.bz2 below; do not extract this archive yourself. Your original file stays untouched.");
            Button download = button("Open OldUnreal 469e downloads", false); download.setOnClickListener(v -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/OldUnreal/UnrealTournamentPatches/releases/tag/v469e"))); }
                catch (android.content.ActivityNotFoundException e) { notice = "No browser is available."; refresh(); }
            }); content.addView(download);
            patchButton = button("Choose 469e patch archive", false); patchButton.setOnClickListener(v -> pickPatch()); content.addView(patchButton);
        } else if (page == GAME_PAGE) {
            heading(content, "Import your game folder", "Select the entire extracted Unreal Tournament parent folder, not an archive or an individual subfolder. It should contain Maps, Textures, Sounds, Music and System. The app makes its own copy and applies the patch there; your original stays untouched.");
            browse = button("Choose and import game folder", false); browse.setOnClickListener(v -> pickFolder()); content.addView(browse);
        } else {
            heading(content, "Ready for the tournament", "Connect a controller before launch or use touch controls.");
            start = button("Play UT99", true); start.setOnClickListener(v -> prepare(true)); content.addView(start);
            join = button("Join a server by address", false); join.setOnClickListener(v -> showJoinServer()); content.addView(join);
            space(content);
            Button manage = button("Manage game copies", false); manage.setOnClickListener(v -> showCopies()); content.addView(manage);
            Button maps = button("Add maps (.unr)", false); maps.setOnClickListener(v -> pickContent(0)); content.addView(maps);
            Button extras = button("Add map dependencies and other files", false); extras.setOnClickListener(v -> {
                String[] choices = {GameContentManager.DESCRIPTIONS[1], GameContentManager.DESCRIPTIONS[2], GameContentManager.DESCRIPTIONS[3], GameContentManager.DESCRIPTIONS[4]};
                new android.app.AlertDialog.Builder(this).setTitle("Choose a dependency type").setItems(choices, (dialog, which) -> pickContent(which + 1)).setNegativeButton("Cancel", null).show();
            }); content.addView(extras);
            space(content);
            Button help = button("Game files and troubleshooting", false); help.setOnClickListener(v -> showHelp()); content.addView(help);
            report = button("Export diagnostics report", false); report.setOnClickListener(v -> exportReports()); content.addView(report);
            space(content);
            Button credits = button("Credits and licences", false); credits.setOnClickListener(v -> showCredits()); content.addView(credits);
        }
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffd48a64));
        progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xffd48a64));
        progressBar.setMax(1000); LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(8)); pp.setMargins(0, dp(18), 0, 0); content.addView(progressBar, pp);
        status = label("", 14, 0xffe0ded7); status.setLineSpacing(dp(3), 1f); status.setPadding(0, dp(12), 0, dp(8)); content.addView(status);
        cancel = button("Cancel import", false); cancel.setOnClickListener(v -> { cancelImport.set(true); workText = "Cancelling import…"; refresh(); }); content.addView(cancel);
        if (page != HOME_PAGE) {
            done = button("Done", true); done.setOnClickListener(v -> {
                if (busy || (page == PATCH_PAGE ? !patchReady() : problem(GameFolderImporter.active(this)) != null)) return;
                String key = page == PATCH_PAGE ? "patch-confirmed" : "game-confirmed";
                if (!getSharedPreferences("setup-pages", MODE_PRIVATE).edit().putBoolean(key, true).commit()) { notice = "Could not save setup progress."; refresh(); return; }
                changePage(page == PATCH_PAGE ? GAME_PAGE : HOME_PAGE);
            }); content.addView(done);
        }
        scroll.addView(outer); frame.addView(scroll, 0); refresh();
    }
    private void space(LinearLayout parent) { android.view.View gap = new android.view.View(this); parent.addView(gap, new LinearLayout.LayoutParams(1, dp(20))); }
    private void showCompatibilityAcknowledgment(FrameLayout frame) {
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), 0, dp(20), 0);
        TextView explanation = new TextView(this); explanation.setTextSize(16); explanation.setLineSpacing(dp(3), 1f);
        explanation.setText("Unreal Tournament was created by Epic Games and Digital Extremes. OldUnreal maintains the 469e patch. TheHexCaster created this independent Android adaptation; it is not endorsed by Epic Games or OldUnreal.\n\n"
                + "This app does not include the UT engine or game files. Please supply your own official 469e patch and game folder.\n\n"
                + (BuildConfig.DIRECT_REHOST ? "This test keeps engine files unchanged on disk and adapts temporary copies in memory for Android. Your selected originals stay untouched.\n\n"
                : BuildConfig.LINUX_RUNTIME_PROBE ? "This test copies engine files unchanged. The Linux runtime is not ready to launch. Your selected originals stay untouched.\n\n"
                : "To make the game work on Android, the app copies and changes parts of the patch for compatibility. Your selected originals stay untouched.\n\n")
                + "Credits and third-party licence texts are available on the setup screen.");
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false); scroll.addView(explanation);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, Math.min(dp(300), getResources().getDisplayMetrics().heightPixels / 3)));
        CheckBox check = new CheckBox(this); check.setText("I have read and understand the above.");
        body.addView(check);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Before you continue").setView(body).setCancelable(false)
                .setNegativeButton("Exit", (d, which) -> finish())
                .setPositiveButton("Continue", null).create();
        dialog.setOnShowListener(ignored -> {
            Button next = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
            next.setEnabled(false);
            check.setOnCheckedChangeListener((button, checked) -> next.setEnabled(checked));
            next.setOnClickListener(v -> {
                if (!check.isChecked()) return;
                if (!getSharedPreferences("setup-acknowledgment", MODE_PRIVATE).edit().putBoolean("compatibility-v1", true).commit()) {
                    check.setError("Could not save your choice. Please try again."); return;
                }
                dialog.dismiss();
                if (getIntent().getBooleanExtra("verify_runtime", false)) prepare(false);
            });
        });
        dialog.show();
    }
    private void showAttribution(FrameLayout frame, Runnable after) {
        LinearLayout intro = new LinearLayout(this); intro.setOrientation(LinearLayout.VERTICAL); intro.setGravity(Gravity.CENTER);
        intro.setPadding(dp(32), dp(32), dp(32), dp(32)); intro.setBackground(new ForgedSteelDrawable());
        TextView brand = label("UTDROID99", 38, 0xfff6eee1); brand.setGravity(Gravity.CENTER);
        brand.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)); brand.setLetterSpacing(.10f); intro.addView(brand);
        TextView line = label("────────────────", 15, 0xffc27857); line.setGravity(Gravity.CENTER); intro.addView(line);
        TextView credits = label("UNREAL TOURNAMENT\nCreated by Epic Games and Digital Extremes\n\n469e patch maintained by OldUnreal\n\nANDROID ADAPTATION\nTheHexCaster\n\nIndependent project; not endorsed by Epic Games or OldUnreal", 16, 0xffdedbd3);
        credits.setGravity(Gravity.CENTER); credits.setLineSpacing(dp(3), 1f); intro.addView(credits);
        TextView hint = label("Tap to continue", 12, 0xffa5a8a5); hint.setGravity(Gravity.CENTER); hint.setPadding(0, dp(24), 0, 0); intro.addView(hint);
        frame.addView(intro, new FrameLayout.LayoutParams(-1, -1));
        intro.setAlpha(0); intro.animate().alpha(1).setDuration(650).start();
        java.util.concurrent.atomic.AtomicBoolean dismissed = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable finish = () -> { if (dismissed.compareAndSet(false, true)) { intro.animate().cancel(); frame.removeView(intro); after.run(); } };
        intro.setOnClickListener(v -> finish.run());
        intro.postDelayed(() -> { if (intro.getParent() != null) intro.animate().alpha(0).setDuration(1000).withEndAction(finish).start(); }, 5500);
    }
    private void showCredits() {
        new android.app.AlertDialog.Builder(this).setTitle("Credits and licences")
            .setMessage("Unreal Tournament: Epic Games and Digital Extremes.\n\nOldUnreal maintains the 469e patch. Android adaptation by TheHexCaster. This independent project is not endorsed by Epic Games or OldUnreal.\n\nSDL2 © 1997–2025 Sam Lantinga. OpenAL Soft © 1999–2007 its authors. libxmp © 1996–2024 Claudio Matsuoka and Hipolito Carraro Jr. libsndfile © 1999–2018 Erik de Castro Lopo. mpg123 © 1995–2020 Michael Hipp and others.\n\nOpenAL Soft, libsndfile and mpg123 use LGPL terms; select Licence texts for their terms and the full component notices. SDL2, OpenAL Soft, libxmp and libsndfile are locally altered.\n\nAttribution does not grant permission to redistribute the engine or game content.")
            .setPositiveButton("Close", null).setNeutralButton("Licence texts", (d, w) -> showLicences()).show();
    }
    private void showLicences() {
        String[] files = {"OldUnreal.txt", "SDL2.txt", "SDL2-yuv2rgb.txt", "SDL2-HIDAPI-licences.txt", "SDL2-HIDAPI-BSD.txt", "SDL2-HIDAPI-original.txt", "SDL2-HIDAPI-GPL3.txt", "OpenAL-Soft.txt", "libxmp.txt", "libxmp-ProWizard.txt", "libxmp-lhasa.txt", "libsndfile.txt", "libsndfile-ALAC-attribution.txt", "libsndfile-ALAC-Apache2.txt", "libsndfile-GSM610.txt", "mpg123.txt", "Android-NDK-LLVM.txt", "Android-NDK-libcxxabi.txt", "UTdroid99.txt"};
        List<String> names = new ArrayList<>(java.util.Arrays.asList(files));
        if (BuildConfig.ENGINE_FREE_EXPERIMENT) {
            names.add("Apache-2.0.txt"); names.add("Apache-Commons-Compress-NOTICE.txt"); names.add("Apache-Commons-IO-NOTICE.txt");
            names.add("Apache-Commons-Codec-NOTICE.txt"); names.add("Apache-Commons-Lang-NOTICE.txt");
        }
        String[] allFiles = names.toArray(new String[0]);
        new android.app.AlertDialog.Builder(this).setTitle("Licence texts").setItems(allFiles, (d, which) -> {
            try (java.io.InputStream in = getAssets().open("licenses/" + allFiles[which]);
                 java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                new android.app.AlertDialog.Builder(this).setTitle(allFiles[which])
                    .setMessage(out.toString("UTF-8")).setPositiveButton("Close", null).show();
            } catch (IOException e) { notice = "Could not read licence: " + e.getMessage(); refresh(); }
        }).setNegativeButton("Close", null).show();
    }
    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(intent, PICK_FOLDER); }
        catch (android.content.ActivityNotFoundException e) { notice = "No folder picker is available."; refresh(); }
    }
    private void pickPatch() {
        if (busy) return;
        if (GameSession.running(this)) { notice = "Exit the game before importing a patch."; refresh(); return; }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(intent, PICK_PATCH); }
        catch (android.content.ActivityNotFoundException e) { notice = "No file picker is available."; refresh(); }
    }
    private void pickContent(int category) {
        if (busy) return;
        if (GameFolderImporter.active(this) == null) { notice = "Import a game folder first."; refresh(); return; }
        if (GameSession.running(this)) { notice = "Exit the game before adding files."; refresh(); return; }
        pendingCategory = category;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(intent, PICK_CONTENT); }
        catch (android.content.ActivityNotFoundException e) { notice = "No file picker is available."; refresh(); }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("content-category", pendingCategory); out.putInt("setup-page", page); super.onSaveInstanceState(out);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || (data.getData() == null && data.getClipData() == null)) {
            if (request == EXPORT_REPORTS) { notice = "Report export cancelled; reports remain in the app."; refresh(); }
            return;
        }
        if (request == PICK_FOLDER) beginImport(data.getData());
        else if (request == PICK_PATCH) beginPatchImport(data.getData());
        else if (request == EXPORT_REPORTS) writeReports(data.getData());
        else if (request == PICK_CONTENT) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) for (int i=0; i<data.getClipData().getItemCount(); ++i) {
                Uri uri=data.getClipData().getItemAt(i).getUri(); if(uri != null) uris.add(uri);
            } else if (data.getData() != null) uris.add(data.getData());
            beginContentImport(pendingCategory, uris);
        }
    }
    private void beginPatchImport(Uri uri) {
        if (busy || uri == null) return;
        busy = true; importing = true; cancelImport.set(false); notice = ""; workText = "Copying patch archive. Please wait…"; workDone = -1; workTotal = -1; refresh();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        new Thread(() -> {
            try {
                PatchImportBridge.backend().importPatch(getApplicationContext(), uri, cancelImport, new PatchImportBridge.Progress() {
                    public void update(String text) { update(text, -1, -1); }
                    public void update(String text, long doneBytes, long totalBytes) {
                        runOnUiThread(() -> { if (!isFinishing() && !isDestroyed() && importing) { workText = text; workDone = doneBytes; workTotal = totalBytes; refresh(); } });
                    }
                });
                runOnUiThread(() -> finishWork("Patch imported and verified. Your original archive is unchanged. Tap Done to continue."));
            } catch (Exception e) {
                runOnUiThread(() -> finishWork("Patch import did not finish: " + e.getMessage() + "\nAny previous patch remains available."));
            }
        }, "patch-import").start();
    }
    private void beginContentImport(int category, List<Uri> uris) {
        File current = GameFolderImporter.active(this);
        if (busy || current == null) { notice = "Import a game folder first."; refresh(); return; }
        final String id=current.getName();
        busy=true; importing=true; cancelImport.set(false); notice="Adding selected files…"; refresh();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        new Thread(() -> {
            try {
                GameContentManager.Result result=GameContentManager.add(getApplicationContext(), id, category, uris, cancelImport,
                    (name, done, total) -> runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) status.setText("Adding file " + done + " of " + total + "\n" + name);
                    }));
                runOnUiThread(() -> finishWork("Added " + result.added + " file(s). " + result.skipped + " existing file(s) were left unchanged."));
            } catch (Exception e) { runOnUiThread(() -> finishWork("Could not add files: " + e.getMessage())); }
        }, "game-content-import").start();
    }
    private void showCopies() {
        if (busy) return;
        if (GameSession.running(this)) { notice="Exit the game before managing game copies."; refresh(); return; }
        busy=true; notice="Checking game copies…"; refresh();
        new Thread(() -> {
            try {
                List<GameContentManager.Copy> copies=GameContentManager.copies(getApplicationContext());
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    busy=false; notice=""; refresh();
                    String[] labels=new String[copies.size() + 1];
                    labels[0] = "Import another game folder…";
                    for(int i=0;i<copies.size();i++) {
                        GameContentManager.Copy copy=copies.get(i);
                        labels[i + 1]=(copy.active ? "Active · " : "Older · ") + java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(copy.time))
                                + " · " + android.text.format.Formatter.formatFileSize(this,copy.bytes);
                    }
                    new android.app.AlertDialog.Builder(this).setTitle("Manage game copies").setItems(labels, (dialog, which) -> {
                        if (which == 0) pickFolder(); else showCopyActions(copies.get(which - 1), labels[which]);
                    })
                        .setNegativeButton("Close",null).show();
                });
            } catch (Exception e) { runOnUiThread(() -> finishWork("Could not list game copies: " + e.getMessage())); }
        }, "list-game-copies").start();
    }
    private void showCopyActions(GameContentManager.Copy copy, String label) {
        if (copy.active) {
            new android.app.AlertDialog.Builder(this).setTitle("Active game copy").setMessage(label + "\n\nSelect another copy before deleting this one.").setPositiveButton("Close",null).show();
            return;
        }
        String[] actions={"Use this copy", "Delete this copy…"};
        new android.app.AlertDialog.Builder(this).setTitle(label).setItems(actions, (dialog, which) -> {
            if (which==0) {
                try { GameContentManager.activate(this,copy.id); notice="Selected the game copy."; }
                catch (IOException e) { notice="Could not select the copy: " + e.getMessage(); }
                refresh();
            } else {
                new android.app.AlertDialog.Builder(this).setTitle("Delete this game copy?")
                    .setMessage(label + "\n\nThis permanently deletes only this app-owned copy and its imported files. The active copy, your original folder, and app settings remain. This cannot be undone.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Delete copy", (confirm, button) -> deleteCopy(copy.id)).show();
            }
        }).setNegativeButton("Cancel",null).show();
    }
    private void deleteCopy(String id) {
        if (busy) return; busy=true; notice="Deleting game copy…"; refresh();
        new Thread(() -> {
            try { GameContentManager.delete(getApplicationContext(),id); runOnUiThread(() -> finishWork("Old game copy deleted.")); }
            catch(Exception e) { runOnUiThread(() -> finishWork("Could not delete game copy: " + e.getMessage())); }
        }, "delete-game-copy").start();
    }
    static String problem(File root) {
        if (root == null) return "No game folder imported yet.";
        for (String folder : new String[]{"Maps", "Textures", "Sounds", "Music", "System"})
            if (!new File(root, folder).isDirectory()) return "The selected parent folder is missing " + folder + ". Select the extracted Unreal Tournament folder.";
        String[] required = {"Maps/CityIntro.unr", "Textures/Palettes.utx", "Textures/UWindowFonts.utx", "System/Core.u", "System/Engine.u", "System/Core.int"};
        List<String> missing = new ArrayList<>();
        for (String name : required) { File file = new File(root, name); if (!file.isFile() || !file.canRead() || file.length() == 0) missing.add(name); }
        return missing.isEmpty() ? null : "Missing or unreadable:\n• " + android.text.TextUtils.join("\n• ", missing);
    }
    private void beginImport(Uri tree) {
        if (busy) return;
        if (GameSession.running(this)) { notice = "Exit the game before importing game files."; refresh(); return; }
        if (BuildConfig.ENGINE_FREE_EXPERIMENT) {
            try { if (PatchImportBridge.backend().active(this) == null) { notice = "Import the 469e patch first."; refresh(); return; } }
            catch (IOException e) { notice = e.getMessage(); refresh(); return; }
        }
        busy = true; importing = true; cancelImport.set(false); notice = ""; workText = "Copying game files. Please wait…"; workDone = -1; workTotal = -1; refresh();
        if (page == HOME_PAGE && pageView != null) pageView.post(() -> pageView.fullScroll(android.view.View.FOCUS_DOWN));
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        new Thread(() -> {
            try {
                GameFolderImporter.copy(getApplicationContext(), tree, cancelImport, (files, bytes, path) -> {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed() && importing) { workText = "Copying game files: " + files + " files, " + (bytes / 1048576) + " MB\n" + path + "\nPlease wait…"; refresh(); }
                    });
                });
                runOnUiThread(() -> finishWork("Game folder imported. Required startup files are present. Your original folder is unchanged."
                        + (page == GAME_PAGE ? " Tap Done to continue." : " The new copy is now active.")));
            } catch (Exception e) {
                runOnUiThread(() -> finishWork("Import did not finish: " + e.getMessage() + "\nAny previously imported game remains available."));
            }
        }, "game-folder-import").start();
    }
    private void finishWork(String message) {
        if (isFinishing() || isDestroyed()) return;
        busy = false; importing = false; workText = ""; workDone = workTotal = -1; setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED); notice = message; refresh();
    }
    private void showHelp() {
        new android.app.AlertDialog.Builder(this).setTitle("Getting UT99 ready")
            .setMessage((BuildConfig.ENGINE_FREE_EXPERIMENT
                    ? "First select OldUnreal-UTPatch469e-Linux-arm64.tar.bz2 from the official 469e downloads. Then select your Unreal Tournament folder containing Maps, Textures, Sounds, Music and System. The app applies the patch to its private copy."
                    : "Select your own prepared Unreal Tournament folder, containing Maps, Textures, Sounds, Music and System with matching OldUnreal 469e files. A stock GOTY System folder alone is not enough.")
                    + "\n\nThe app copies the selected folder. Add .unr maps and their dependencies separately; existing filenames are never overwritten. Manage game copies lets you select or delete an old copy.\n\nApp data, including imported files and settings, is removed on uninstall. Reports may contain device details, paths and game logs; review them before sharing.")
            .setPositiveButton("Got it", null).show();
    }
    private void prepare(boolean launch) {
        prepare(launch, null);
    }
    private void showJoinServer() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("server.example.com:7777");
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).setTitle("Join a UT99 server")
                .setMessage("Enter its hostname or IPv4 address and optional game port.")
                .setView(input).setNegativeButton("Cancel", null).setPositiveButton("Join", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { String address = ServerAddress.parse(input.getText().toString()); dialog.dismiss(); prepare(true, address); }
            catch (IllegalArgumentException e) { input.setError(e.getMessage()); }
        }));
        dialog.show();
    }
    private void prepare(boolean launch, String server) {
        if (BuildConfig.LINUX_RUNTIME_PROBE) { notice = "Linux runtime is not integrated yet."; refresh(); return; }
        if (busy || (launch && problem(GameFolderImporter.active(this)) != null)) return;
        busy = true; notice = "Preparing the game…"; refresh();
        new Thread(() -> {
            try {
                if (BuildConfig.ENGINE_FREE_EXPERIMENT) PatchImportBridge.backend().installRuntime(this);
                else SharedRuntimeInstaller.install(this);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    busy = false;
                    if (launch && problem(GameFolderImporter.active(this)) == null) {
                        CrashReporter.markLaunch(this);
                        File imported = GameFolderImporter.active(this);
                        Intent game = new Intent(this, ExperimentalSDLActivity.class).putExtra("import-id", imported.getName());
                        if (server != null) game.putExtra("server-address", server);
                        startActivity(game);
                    } else { notice = "Runtime prepared."; refresh(); }
                });
            } catch (Exception e) {
                CrashReporter.writeSetupFailure(this, e);
                runOnUiThread(() -> finishWork("Setup failed: " + e.getMessage()));
            }
        }, "runtime-setup").start();
    }
    private void exportReports() {
        if (busy) return;
        busy = true; notice = "Preparing diagnostic reports…"; refresh();
        new Thread(() -> {
            try {
                String issue = problem(GameFolderImporter.active(this));
                CrashReporter.writeManual(this, issue == null ? "Required game files found" : issue);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    busy = false; refresh();
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/zip").putExtra(Intent.EXTRA_TITLE, "UT99-diagnostic-reports.zip");
                    try { startActivityForResult(intent, EXPORT_REPORTS); }
                    catch (android.content.ActivityNotFoundException e) { notice = "No file saver is available."; refresh(); }
                });
            } catch (Exception e) { runOnUiThread(() -> finishWork("Could not prepare reports: " + e.getMessage())); }
        }, "manual-report").start();
    }
    private void writeReports(Uri destination) {
        busy = true; notice = "Exporting reports…"; refresh();
        new Thread(() -> {
            try (OutputStream stream = getContentResolver().openOutputStream(destination, "w")) {
                if (stream == null) throw new IOException("Cannot open selected destination.");
                try (ZipOutputStream zip = new ZipOutputStream(stream)) {
                    File[] files = CrashReporter.reports(this).listFiles();
                    if (files != null) for (File file : files) if (file.isFile()) {
                        zip.putNextEntry(new ZipEntry(file.getName()));
                        try (FileInputStream in = new FileInputStream(file)) {
                            byte[] buffer = new byte[65536]; int n;
                            while ((n = in.read(buffer)) != -1) zip.write(buffer, 0, n);
                        }
                        zip.closeEntry();
                    }
                }
                runOnUiThread(() -> finishWork("Reports exported. Review them before sharing."));
            } catch (Exception e) { runOnUiThread(() -> finishWork("Could not export reports: " + e.getMessage())); }
        }, "report-export").start();
    }
    private void refresh() {
        File root = GameFolderImporter.active(this); String issue = problem(root);
        boolean ready = issue == null;
        boolean hasPatch = patchReady();
        boolean complete = page == PATCH_PAGE ? hasPatch : page == GAME_PAGE ? ready : ready && hasPatch;
        if (readiness == null || status == null) return;
        readiness.setText(importing ? (page == PATCH_PAGE ? "PATCH  /  WORKING" : "GAME FILES  /  WORKING")
                : page == PATCH_PAGE ? (hasPatch ? "PATCH  /  READY" : "PATCH  /  NEEDED")
                : page == GAME_PAGE ? (ready ? "GAME FILES  /  READY" : "GAME FILES  /  NEEDED")
                : (complete ? "READY TO PLAY" : "SETUP REQUIRED"));
        readiness.setBackground(panel(complete ? 0xff344a3c : 0xff5a3d31, complete ? 0xff819482 : 0xffb97950));
        String fallback = page == PATCH_PAGE ? (hasPatch ? "Patch verified and stored in the app. Tap Done to continue." : "Choose the Linux ARM64 .tar.bz2 patch archive.")
                : page == GAME_PAGE ? (ready ? "Game folder stored. Required startup files are present. Tap Done to continue." : issue)
                : (complete ? "" : (hasPatch ? issue : "The imported patch is missing."));
        String shownWork = workText;
        if (importing && workDone >= 0 && workTotal > 0)
            shownWork += "\n" + Math.min(100, workDone * 100 / workTotal) + "%";
        status.setText(busy && !workText.isEmpty() ? shownWork : !notice.isEmpty() ? notice : fallback);
        if (progressBar != null) {
            progressBar.setVisibility(importing ? android.view.View.VISIBLE : android.view.View.GONE);
            boolean measured = importing && workDone >= 0 && workTotal > 0;
            progressBar.setIndeterminate(!measured);
            if (measured) progressBar.setProgress((int)Math.min(1000, workDone * 1000 / workTotal));
        }
        if (start != null) { start.setEnabled(ready && hasPatch && !busy && !BuildConfig.LINUX_RUNTIME_PROBE); start.setAlpha(start.isEnabled() ? 1f : .45f); }
        if (join != null) { join.setEnabled(start.isEnabled()); join.setAlpha(join.isEnabled() ? 1f : .45f); }
        if (browse != null) { browse.setEnabled(!busy); browse.setAlpha(browse.isEnabled() ? 1f : .45f); }
        if (patchButton != null) patchButton.setEnabled(!busy);
        if (done != null) { done.setEnabled(complete && !busy); done.setAlpha(done.isEnabled() ? 1f : .45f); }
        if (report != null) report.setEnabled(!busy);
        if (cancel != null) cancel.setVisibility(importing && !cancelImport.get() ? android.view.View.VISIBLE : android.view.View.GONE);
    }
    @Override protected void onResume() {
        super.onResume(); if (!busy) refresh(); CrashReporter.check(this);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> CrashReporter.check(this), 4000);
    }
    @Override protected void onDestroy() { if (importing) cancelImport.set(true); super.onDestroy(); }
}
