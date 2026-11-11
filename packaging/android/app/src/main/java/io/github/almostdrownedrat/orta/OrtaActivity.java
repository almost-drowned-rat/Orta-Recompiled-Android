// Panzer Dragoon Orta Recomp - the Android activity (SDL3's SDLActivity loads libmain.so and calls SDL_main).
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.almostdrownedrat.orta;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.libsdl.app.SDLActivity;

public class OrtaActivity extends SDLActivity {
    private static final String TAG = "ORTA";

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL3", "python3.15", "main" };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // python = CPython's stdlib + site-packages (the lifter, capstone); toolchain = the on-device compiler's
        // sysroot + clang resource dir (clang / lld themselves are lib*_exe.so in nativeLibraryDir); probe = agent
        // B's toolchain probe (dev, stage 1)
        // tools = the launcher's read-only folder (release/toolchain/lift/build.txt, the port's sources, the runtime
        // SDK, the compiler's sysroot + resource dir: tools/toolchain)
        // turnip = the Mesa Turnip driver for Adreno GPUs without Vulkan 1.3 (vkshim/vulkan_orta.c)
        // (a Turnip driver is never shipped: the player picks one when the device needs it - importDriver)
        for (String a : new String[] { "python", "tools", "probe" }) extractOnce(a);
        super.onCreate(savedInstanceState);
        // forced landscape (maintainer 2026-11-11), both ways round; loader.c sets SDL's orientation hint to match
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        // the first start builds the game for many minutes: the screen stays on (also while playing)
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // BACK: with targetSdk 36 the back gesture / key reaches an app only through OnBackInvokedCallback
        // (predictive back; onBackPressed and KEYCODE_BACK are no longer delivered) - handed on to SDL as AC_BACK
        // BACK opens the Android options (maintainer 2026-11-11: hide / move / hide single touch buttons); in the
        // touch layout editor it ends editing
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                    Log.i(TAG, "[BACK] back gesture / key");
                    onBackMenu();
                });
        }
        // the touch controls over SDL's surface (TouchOverlay.java)
        mOverlay = new TouchOverlay(this);
        mLayout.addView(mOverlay, new android.widget.RelativeLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        // the BACK menu: a panel sliding in from the left (SideMenu.java), above the touch controls
        mMenu = new SideMenu(this);
        mLayout.addView(mMenu, new android.widget.RelativeLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private TouchOverlay mOverlay;
    private SideMenu mMenu;

    /** Called through JNI by loader.c right before orta_game_main (any thread). */
    public static void onGameStarting() {
        final OrtaActivity a = (OrtaActivity) mSingleton;
        if (a != null) a.runOnUiThread(() -> { if (a.mOverlay != null) a.mOverlay.setGameRunning(); });
    }
    private static native void nativeTouchPadEnable(boolean on);
    private static native String nativePadPorts();
    private static native void nativeSwapPorts(int a, int b);

    /** touchpad.c's pad ranking (any thread): the gamepads Android reports as built in (not USB / Bluetooth / a
     *  dock's), one name per line - the toolkit puts external pads on the first ports (agent-l/pads). */
    public static String builtInPadNames() {
        StringBuilder b = new StringBuilder();
        for (int id : android.view.InputDevice.getDeviceIds()) {
            android.view.InputDevice d = android.view.InputDevice.getDevice(id);
            if (d == null || d.isVirtual() || d.isExternal() || !fromGamepad(d.getSources())) continue;
            b.append(d.getName()).append('\n');
        }
        return b.toString();
    }

    private static boolean fromGamepad(int source) {
        return (source & android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD
            || (source & android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK;
    }

    private void gamepadUsed() {
        if (mOverlay == null || mOverlay.getVisibility() != android.view.View.VISIBLE
            || mOverlay.getMode() != TouchOverlay.MODE_AUTO || mOverlay.isEditing()) return;
        mOverlay.onGamepadInput();
        try { nativeTouchPadEnable(false); } catch (UnsatisfiedLinkError ignored) { }
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        // the open menu takes every key (pad / keyboard), the game sees none; BACK itself comes through
        // OnBackInvokedCallback (onBackMenu) on Android 13+, as a key only on older ones
        if (mMenu != null && mMenu.isOpen()) {
            if (e.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK && android.os.Build.VERSION.SDK_INT >= 33)
                return super.dispatchKeyEvent(e);
            return mMenu.onKey(e);
        }
        if (fromGamepad(e.getSource()) && e.getKeyCode() != android.view.KeyEvent.KEYCODE_BACK) gamepadUsed();
        return super.dispatchKeyEvent(e);
    }

    @Override
    public boolean dispatchGenericMotionEvent(android.view.MotionEvent e) {
        if (mMenu != null && mMenu.isOpen() && fromGamepad(e.getSource())) return mMenu.onMotion(e);
        if (fromGamepad(e.getSource())) gamepadUsed();
        return super.dispatchGenericMotionEvent(e);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        // a touch brings the hidden (AUTO) controls back - the hidden view itself gets no touches
        if (mOverlay != null && (mMenu == null || !mMenu.isOpen()) && e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN
            && mOverlay.getMode() == TouchOverlay.MODE_AUTO && mOverlay.getVisibility() != android.view.View.VISIBLE) {
            try { nativeTouchPadEnable(true); } catch (UnsatisfiedLinkError ignored) { }
            mOverlay.onTouchEvent(android.view.MotionEvent.obtain(e.getDownTime(), e.getEventTime(),
                android.view.MotionEvent.ACTION_CANCEL, e.getX(), e.getY(), 0));
        }
        return super.dispatchTouchEvent(e);
    }

    private static final int PICK_DRIVER = 4711;

    /** BACK menu > Graphics driver: choose a Turnip package, or go back to the system driver; both restart the app
     *  (the Vulkan library picks its driver once per process). */
    private void driverMenu() {
        String[] items = { "Choose a Turnip driver package (.adpkg.zip)...", "Use the system driver" };
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Graphics driver (restarts the game)")
            .setItems(items, (d, which) -> {
                if (which == 0) {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, PICK_DRIVER);
                } else {
                    removeDriver(this);
                    restartApp();
                    System.exit(0);
                }
            }).show();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (request == PICK_DRIVER) {
            if (result == RESULT_OK && data != null && data.getData() != null) {
                String err = importDriver(data.getData().toString());
                if (err.isEmpty()) { restartApp(); System.exit(0); }
                new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Graphics driver").setMessage(err).setPositiveButton("OK", null).show();
            }
            return;
        }
        super.onActivityResult(request, result, data);
    }

    /** BACK: the Android options as a panel from the left (SideMenu; maintainer 2026-11-11: Dolphin-like, the game
     *  keeps running behind it - freezing is a later toolkit item, and no START is sent). BACK in the open menu goes
     *  back one level / closes it; in the touch layout editor it ends editing. */
    private void onBackMenu() {
        if (mOverlay == null || !mOverlay.isGameRunning()) return;     // the installer: BACK does nothing
        if (mMenu.isOpen()) { mMenu.back(); return; }
        if (mOverlay.isEditing()) { mOverlay.setEditing(false); return; }
        mOverlay.release();
        mMenu.open(rootPage());
    }

    private static String pct(float f) { return Math.round(f * 100) + " %"; }

    /** The root page (maintainer 2026-11-11: move, resize, hide single buttons, touch buttons off (Odin 2 Portal),
     *  a Turnip driver also on devices whose own driver has Vulkan 1.3, stick sensitivity). */
    private SideMenu.Page rootPage() {
        final String[] modes = { "Automatic", "Always", "Off" };
        return new SideMenu.Page() {
            public String title() { return "Panzer Dragoon Orta Recomp"; }
            public java.util.List<SideMenu.Item> items() {
                java.util.List<SideMenu.Item> l = new java.util.ArrayList<>();
                l.add(new SideMenu.Item("Back to the game") { @Override void run() { mMenu.close(); } });
                l.add(new SideMenu.Item("Touch buttons") {     // Automatic = hidden while a gamepad is used
                    @Override String value() { return modes[mOverlay.getMode()]; }
                    @Override void step(int d) {
                        int m = (mOverlay.getMode() + d + 3) % 3;
                        mOverlay.setMode(m);
                        try { nativeTouchPadEnable(m != TouchOverlay.MODE_OFF); } catch (UnsatisfiedLinkError ignored) { }
                    }
                });
                l.add(new SideMenu.Item("Controllers") { @Override void run() { mMenu.push(padPage()); } });
                l.add(new SideMenu.Item("Move and resize touch buttons") {
                    @Override void run() { mMenu.close(); mOverlay.setEditing(true); }
                });
                l.add(new SideMenu.Item("Button size") {       // all buttons, 50-150 %
                    @Override String value() { return pct(mOverlay.getScale()); }
                    @Override void step(int d) { mOverlay.setScale(Math.max(0.5f, Math.min(1.5f, mOverlay.getScale() + 0.25f * d))); }
                });
                l.add(new SideMenu.Item("Hide single touch buttons") {
                    @Override void run() { mMenu.push(hidePage()); }
                });
                l.add(new SideMenu.Item("Opacity") {           // 25-100 %
                    @Override String value() { return mOverlay.getOpacity() + " %"; }
                    @Override void step(int d) { mOverlay.setOpacity(Math.max(25, Math.min(100, mOverlay.getOpacity() + 25 * d))); }
                });
                l.add(new SideMenu.Item("Stick sensitivity") { // a faster cursor: full tilt with a shorter thumb move
                    @Override String value() { return pct(mOverlay.getSensitivity()); }
                    @Override void step(int d) { mOverlay.setSensitivity(Math.max(1f, Math.min(3f, mOverlay.getSensitivity() + 0.5f * d))); }
                });
                l.add(new SideMenu.Item("Reset touch layout") {
                    @Override void run() { mOverlay.resetLayout(); mMenu.refresh(); }
                });
                String drv = driverName(OrtaActivity.this);
                l.add(new SideMenu.Item("Graphics driver: " + (drv != null ? drv : "system")) {
                    @Override void run() { mMenu.close(); driverMenu(); }
                });
                return l;
            }
        };
    }

    /** Which controller is which player (toolkit agent-l/pads). Orta: every controller is player 1 (merged), so the
     *  page only lists them; a multi-player title: a controller line moves that pad to player 1 (swap). */
    private SideMenu.Page padPage() {
        return new SideMenu.Page() {
            public String title() { return "Controllers"; }
            public java.util.List<SideMenu.Item> items() {
                java.util.List<SideMenu.Item> l = new java.util.ArrayList<>();
                String info;
                try { info = nativePadPorts(); } catch (UnsatisfiedLinkError e) { info = ""; }
                String[] lines = info.split("\n");
                if (info.isEmpty()) {
                    l.add(new SideMenu.Item("(this game build lists no controllers yet)"));
                    return l;
                }
                final boolean merged = lines[0].equals("merged");
                String builtIn = "\n" + builtInPadNames();
                if (merged) l.add(new SideMenu.Item("Every controller plays as player 1"));
                int n = 0;
                for (int i = 1; i < lines.length; i++) {
                    String[] f = lines[i].split("\t", 2);
                    if (f.length < 2 || f[1].isEmpty()) continue;
                    final int port = Integer.parseInt(f[0]);
                    String kind = f[1].equals("Orta touch controls") ? "touch" : builtIn.contains("\n" + f[1] + "\n") ? "built in" : "external";
                    String t = (merged ? "" : "Player " + (port + 1) + ":  ") + f[1] + "  (" + kind + ")";
                    l.add(new SideMenu.Item(t + (merged || port == 0 ? "" : "  - make player 1")) {
                        @Override void run() {
                            if (!merged && port != 0) { nativeSwapPorts(port, 0); mMenu.refresh(); }
                        }
                    });
                    n++;
                }
                if (n == 0) l.add(new SideMenu.Item("No controller connected"));
                return l;
            }
        };
    }

    private SideMenu.Page hidePage() {
        return new SideMenu.Page() {
            public String title() { return "Hide single touch buttons"; }
            public java.util.List<SideMenu.Item> items() {
                java.util.List<SideMenu.Item> l = new java.util.ArrayList<>();
                for (int i = 0; i < TouchOverlay.N; i++) {
                    final int b = i;
                    l.add(new SideMenu.Item(TouchOverlay.NAMES[b]) {
                        @Override String value() { return mOverlay.isHidden(b) ? "hidden" : "shown"; }
                        @Override void step(int d) { mOverlay.setHidden(b, !mOverlay.isHidden(b)); }
                    });
                }
                return l;
            }
        };
    }

    /** The launcher's "Choose image...": SDL's file dialog gives a content:// URI, the installer reads files by path
     *  (open / fopen / stat). A path to the picked document itself does not exist for the app (no storage
     *  permission; reopening /proc/self/fd/N of the picker's descriptor goes through the FUSE path again and is
     *  refused - S23 2026-11-11 "permission denied"), so the image is copied once into <files>/data/import/
     *  under its own name; loader.c deletes the copy when the launcher is done (the player's file stays where it
     *  was). Returns the copy's path, "" on failure. Called through JNI from the launcher's thread. */
    public static String importContent(String uri) {
        Context ctx = mSingleton.getApplicationContext();
        android.net.Uri u = android.net.Uri.parse(uri);
        String name = "disc.iso";
        try (android.database.Cursor c = ctx.getContentResolver().query(u,
                new String[] { android.provider.OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (c != null && c.moveToFirst() && c.getString(0) != null) name = c.getString(0).replace('/', '_');
        } catch (Exception e) { /* keep the default name */ }
        File dir = new File(getImportDir(ctx));
        deleteTree(dir);
        dir.mkdirs();
        File out = new File(dir, name);
        long t0 = System.currentTimeMillis(), n = 0;
        try (InputStream in = ctx.getContentResolver().openInputStream(u);
             OutputStream o = new FileOutputStream(out)) {
            if (in == null) return "";
            byte[] buf = new byte[1 << 20];
            int r;
            while ((r = in.read(buf)) > 0) { o.write(buf, 0, r); n += r; }
        } catch (Exception e) {
            Log.e(TAG, "[PICK] cannot copy " + uri + ": " + e);
            deleteTree(dir);
            return "";
        }
        Log.i(TAG, "[PICK] " + uri + " -> " + out + " (" + n + " bytes in " + (System.currentTimeMillis() - t0) + " ms)");
        return out.getAbsolutePath();
    }

    /** A Turnip driver package the player picked (maintainer 2026-11-11: the driver is chosen like the ISO, not
     *  shipped): an adrenotools .adpkg.zip (meta.json + the driver .so, e.g. purple-turnip). Unpacked into
     *  <files>/turnip/ (replacing an older one); driver.txt = the library name + the package name for
     *  vkshim/vulkan_orta.c. Returns "" on success, else a sentence for the player. Called through JNI or the menu. */
    public static String importDriver(String uri) {
        Context ctx = mSingleton.getApplicationContext();
        File dir = new File(ctx.getFilesDir(), "turnip"), tmp = new File(ctx.getFilesDir(), "data/tmp/driver_new");
        String lib = null, name = "Turnip";
        deleteTree(tmp);
        tmp.mkdirs();
        try (InputStream in = ctx.getContentResolver().openInputStream(android.net.Uri.parse(uri));
             java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(in)) {
            java.util.zip.ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String n = new File(e.getName()).getName();      // flat: no folders, no ".."
                if (e.isDirectory() || !(n.equals("meta.json") || n.endsWith(".so"))) continue;
                try (OutputStream o = new FileOutputStream(new File(tmp, n))) {
                    byte[] buf = new byte[65536];
                    int r;
                    while ((r = z.read(buf)) > 0) o.write(buf, 0, r);
                }
            }
            File meta = new File(tmp, "meta.json");
            if (!meta.exists()) { deleteTree(tmp); return "This file is not a driver package (no meta.json). Please choose a Turnip .adpkg.zip."; }
            org.json.JSONObject j = new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(meta.toPath())));
            lib = j.optString("libraryName", null);
            name = j.optString("name", name) + " " + j.optString("driverVersion", "");
            if (lib == null || !new File(tmp, new File(lib).getName()).exists()) {
                deleteTree(tmp);
                return "The driver package names a library it does not contain.";
            }
        } catch (Exception ex) {
            deleteTree(tmp);
            Log.e(TAG, "[DRIVER] cannot import " + uri + ": " + ex);
            return "The driver package could not be read: " + ex.getMessage();
        }
        deleteTree(dir);
        if (!tmp.renameTo(dir)) return "The driver could not be installed (storage).";
        try (OutputStream o = new FileOutputStream(new File(dir, "driver.txt"))) {
            o.write((new File(lib).getName() + "\n" + name.trim() + "\n").getBytes());
        } catch (IOException ex) {
            return "The driver could not be installed (storage).";
        }
        Log.i(TAG, "[DRIVER] installed " + name + " (" + lib + ")");
        return "";
    }

    /** "Use the system driver": the picked Turnip driver is removed. */
    static void removeDriver(Context ctx) { deleteTree(new File(ctx.getFilesDir(), "turnip")); }

    static String driverName(Context ctx) {
        try {
            String[] l = new String(java.nio.file.Files.readAllBytes(new File(ctx.getFilesDir(), "turnip/driver.txt").toPath())).split("\n");
            return l.length > 1 ? l[1] : l[0];
        } catch (Exception e) { return null; }
    }

    static String getImportDir(Context ctx) {
        return new File(ctx.getFilesDir(), "data/import").getAbsolutePath();
    }

    /** The APK's assets/<name> into internal storage, once per APK version (the marker file holds the versionCode +
     *  update time). Android is not portable (maintainer 2026-11-11): everything lives in the app's internal storage. */
    private void extractOnce(String name) {
        try { if (getAssets().list(name).length == 0) return; } catch (IOException e) { return; }
        File dest = new File(getFilesDir(), name);
        File marker = new File(dest, ".apk_version");
        String want;
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            want = pi.getLongVersionCode() + ":" + pi.lastUpdateTime;
        } catch (Exception e) {
            want = "unknown";
        }
        try {
            if (marker.exists() && want.equals(new String(java.nio.file.Files.readAllBytes(marker.toPath())))) return;
            long t0 = System.currentTimeMillis();
            deleteTree(dest);
            int n = copyAssets(getAssets(), name, dest);
            try (OutputStream o = new FileOutputStream(marker)) { o.write(want.getBytes()); }
            Log.i(TAG, "[ASSETS] " + name + " extracted: " + n + " files in " + (System.currentTimeMillis() - t0) + " ms");
        } catch (IOException e) {
            Log.e(TAG, "[ASSETS] " + name + " extraction failed: " + e);
        }
    }

    private static int copyAssets(AssetManager am, String path, File to) throws IOException {
        String[] kids = am.list(path);
        if (kids == null || kids.length == 0) {                 // a file
            to.getParentFile().mkdirs();
            try (InputStream in = am.open(path); OutputStream out = new FileOutputStream(to)) {
                byte[] buf = new byte[65536];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            }
            return 1;
        }
        int n = 0;
        to.mkdirs();
        for (String k : kids) n += copyAssets(am, path + "/" + k, new File(to, k));
        return n;
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    /** RESTART NOW (host_restart in src/platform/host_linux.c, called through JNI): an app cannot exec itself, so a
     *  fresh launch of this activity is scheduled a moment ahead; the native side ends the process right after. */
    public void restartApp() {
        Context ctx = getApplicationContext();
        Intent start = new Intent(ctx, OrtaActivity.class);
        start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, 1, start,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.set(AlarmManager.RTC, System.currentTimeMillis() + 300, pi);
    }
}
