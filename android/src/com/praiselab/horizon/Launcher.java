package com.praiselab.horizon;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.SearchManager;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.Telephony;
import android.speech.RecognizerIntent;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Horizon home screen: Now feed ← vertically scrolling live tiles → Widgets page,
 * with a dock, search, app drawer, inline folders, a swipe-down Control menu and switchable looks.
 */
public class Launcher extends Activity implements Workspace.Listener {
    static final int REQ_CAL = 7, REQ_LOC = 8, REQ_BIND = 11, REQ_CONFIG = 12;
    static final int HOST_ID = 1024;
    private static final List<String> CALENDARS = Arrays.asList("com.google.android.calendar", "com.samsung.android.calendar", "com.android.calendar");
    static Launcher current;

    Prefs prefs;
    Store store;
    Apps apps;
    LauncherApps la;
    AppWidgetManager awm;
    AppWidgetHost host;
    U.Pal pal;
    boolean darkText;
    int insetTop, insetBottom, cols = 6, iconPx;

    DragLayer root;
    WallpaperView wallpaper;
    Workspace workspace;
    ScrollView homeScroll;
    CellLayout home, hotseat;
    final List<WidgetPanel> panels = new ArrayList<>();
    LinearLayout bottom, searchBar, tray;
    PageIndicator indicator;
    AllAppsView allApps;
    FeedView feed;
    GlanceView glance;
    FolderPopup folder;
    View sheet;
    QuickMenu quick;

    final Map<Long, View> views = new HashMap<>();
    private final Map<Long, Bitmap> shortcutIcons = new HashMap<>();
    private final Map<String, Integer> tileColors = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Item inlineFolder;
    // tilt parallax
    private android.hardware.SensorManager sensors;
    private float[] tiltBase;
    private float tiltX, tiltY, drawnX, drawnY;
    private final android.hardware.SensorEventListener tilt = new android.hardware.SensorEventListener() {
        private final float[] rot = new float[9], ori = new float[3];
        @Override public void onSensorChanged(android.hardware.SensorEvent e) {
            android.hardware.SensorManager.getRotationMatrixFromVector(rot, e.values);
            android.hardware.SensorManager.getOrientation(rot, ori); // ori[1] pitch, ori[2] roll, radians
            if (tiltBase == null) tiltBase = new float[]{ori[1], ori[2]};
            tiltBase[0] += (ori[1] - tiltBase[0]) * 0.015f; // slowly re-centre on how the phone is held
            tiltBase[1] += (ori[2] - tiltBase[1]) * 0.015f;
            float dx = U.clamp(ori[2] - tiltBase[1], -0.3f, 0.3f) / 0.3f, dy = U.clamp(ori[1] - tiltBase[0], -0.3f, 0.3f) / 0.3f;
            float nx = tiltX + (dx - tiltX) * 0.18f, ny = tiltY + (dy - tiltY) * 0.18f;
            // dead zone: sensor jitter while the phone is still must not redraw anything (idle = zero frames)
            if (Math.abs(nx - drawnX) < 0.004f && Math.abs(ny - drawnY) < 0.004f) { tiltX = nx; tiltY = ny; return; }
            tiltX = drawnX = nx;
            tiltY = drawnY = ny;
            applyTilt();
        }
        @Override public void onAccuracyChanged(android.hardware.Sensor s, int a) { }
    };
    private Cloud cloud;
    private String wxText;
    private int wxGlyph;
    private long wxAt;
    private FolderPanel inlinePanel;
    private long prefStamp = -1;
    private float pagePos, drawerProgress;
    private Item pendingWidget;
    private AppWidgetProviderInfo pendingInfo;
    private LauncherApps.Callback appsCallback;
    private BroadcastReceiver tick;
    private WallpaperManager.OnColorsChangedListener wallpaperListener;

    // ====================================================================== lifecycle

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Prefs.scaled(base));
        sizeKey = new Prefs(base).textPct() * 1000 + new Prefs(base).uiPct();
    }

    private int sizeKey;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        T0 = android.os.SystemClock.uptimeMillis();
        current = this;
        U.init(this);
        Window w = getWindow();
        w.setDecorFitsSystemWindows(false);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.setNavigationBarContrastEnforced(false);
        w.getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;

        prefs = new Prefs(this);
        store = new Store(this);
        apps = new Apps(this);
        la = getSystemService(LauncherApps.class);
        awm = AppWidgetManager.getInstance(this);
        host = new AppWidgetHost(this, HOST_ID);
        readWallpaperHints();
        applyPrefs();
        mark("prefs+store");
        buildViews();
        mark("buildViews");
        apps.load();
        mark("apps.load");
        if (store.items.isEmpty()) defaultLayout();
        keepDockSize();
        if (!prefs.sp.getBoolean("visionos2", false)) { // the Spatial look uses each app's real colours, like visionOS
            prefs.sp.edit().putBoolean("visionos2", true).putString("iconStyle", "color").apply();
            applyPrefs();
        }
        if (!prefs.sp.getBoolean("visionos1", false)) { // switch to the new Apple Spatial (visionOS) look
            prefs.sp.edit().putBoolean("visionos1", true).putString("style", "spatial").putString("mode", "dark").apply();
            applyPrefs();
        }
        if (!prefs.sp.getBoolean("themedBack1", false)) { // you prefer the soft themed icons on Home
            prefs.sp.edit().putBoolean("themedBack1", true).putBoolean("colorIcons1", true).putString("iconStyle", "themed").apply();
            applyPrefs();
        }
        if (!prefs.sp.getBoolean("stackClock3", false)) { // put the Horizon clock card in front of its stack
            prefs.sp.edit().putBoolean("stackClock3", true).apply();
            for (Item i : store.items)
                if (i.type == Item.GLANCE && store.byId(i.container) != null && store.byId(i.container).type == Item.STACK) StackView.remember(this, i.container, i.x);
        }
        if (!prefs.sp.getBoolean("apple1", false)) { // iOS look: original-colour icons
            prefs.sp.edit().putBoolean("apple1", true).putString("iconStyle", "color").apply();
            applyPrefs();
        }
        if (!prefs.sp.getBoolean("hzClock1", false)) { // Horizon's own clock card replaces Google Clock in the top stack
            prefs.sp.edit().putBoolean("hzClock1", true).putBoolean("glanceOff", true).apply();
            Item clock = null;
            for (Item i : store.items)
                if (i.type == Item.WIDGET && i.provider.startsWith("com.google.android.deskclock/") && store.byId(i.container) != null
                        && store.byId(i.container).type == Item.STACK) clock = i;
            if (clock != null) {
                for (Item i : new ArrayList<>(store.items)) if (i.type == Item.GLANCE) store.items.remove(i);
                try { host.deleteAppWidgetId(clock.widgetId); } catch (Exception ignored) { }
                Item g = new Item();
                g.type = Item.GLANCE;
                g.container = clock.container;
                g.x = clock.x;
                store.items.remove(clock);
                store.add(g);
                StackView.remember(this, g.container, g.x);
                prefs.sp.edit().putBoolean("glance", true).apply();
                store.save();
            }
        }
        if (!prefs.sp.getBoolean("kbOff1", false)) { // keyboard only when you tap the search bar
            prefs.sp.edit().putBoolean("kbOff1", true).putBoolean("spotlight1", true).putBoolean("drawerKeyboard", false).apply();
        }
        if (!prefs.sp.getBoolean("spotlight1", false)) { // swipe up opens search with the keyboard, like Spotlight
            prefs.sp.edit().putBoolean("spotlight1", true).putBoolean("drawerKeyboard", true).apply();
        }
        if (!prefs.sp.getBoolean("packed1", false)) { // pack Home to the top so nothing needs scrolling
            prefs.sp.edit().putBoolean("packed1", true).apply();
            if (!store.items.isEmpty()) reflow(cols);
        }
        if (!prefs.sp.getBoolean("glanceOff", false)) { // you asked for no clock card on Home (Settings > Clock and date brings it back)
            prefs.sp.edit().putBoolean("glance", false).putBoolean("glanceOff", true).apply();
            ensureGlance();
        }
        if (!prefs.sp.getBoolean("cols5", false)) { // one-time move to the 5-column grid
            int was = prefs.cols();
            prefs.sp.edit().putInt("cols", 5).putBoolean("cols5", true).apply();
            cols = 5;
            if (!store.items.isEmpty() && was != 5) reflow(was);
        }
        if (!store.items.isEmpty() && outOfGrid()) fixGrid();
        mark("migrations");
        bind();
        mark("bind");
        startGoogleFeed();
        mark("feed");
        handler.postDelayed(() -> allApps.prewarm(), 1500); // build the App Library quietly, after Home is up
        if (!prefs.sp.getBoolean("wxStill1", false)) { // a still weather scene by default: no battery cost
            prefs.sp.edit().putBoolean("wxStill1", true).putBoolean("wxAnimated", false).apply();
        }
        if (!prefs.sp.getBoolean("media1", false)) { // you chose a slim 5 x 1 Now Playing card
            prefs.sp.edit().putBoolean("media1", true).putBoolean("media2", true).putBoolean("media3", true).apply();
            boolean changed = false;
            for (Item i : store.items) if (i.type == Item.MEDIA && i.container == Item.DESKTOP && (i.spanY != 1 || i.spanX != cols)) { i.spanY = 1; i.spanX = cols; changed = true; }
            if (changed) { store.save(); home.post(this::anchorHome); }
        }
        if (!prefs.sp.getBoolean("media2", false)) { // back to two rows: the card is now compact enough
            prefs.sp.edit().putBoolean("media2", true).putBoolean("media3", true).apply();
            boolean changed = false;
            for (Item i : store.items) if (i.type == Item.MEDIA && i.container == Item.DESKTOP && i.spanY > 2) { i.spanY = 2; changed = true; }
            if (changed) { store.save(); home.post(this::anchorHome); }
        }
        if (!prefs.sp.getBoolean("anchor1", false)) { // icons gather at the bottom, widgets at the top
            prefs.sp.edit().putBoolean("anchor1", true).apply();
            home.post(this::anchorHome);
        }
        allApps.filter();

        appsCallback = new LauncherApps.Callback() {
            @Override public void onPackageRemoved(String pkg, UserHandle u) { packageRemoved(pkg, u); }
            @Override public void onPackageAdded(String pkg, UserHandle u) { appsChanged(); autoAdd(pkg, u); }
            @Override public void onPackageChanged(String pkg, UserHandle u) { appsChanged(); }
            @Override public void onPackagesAvailable(String[] p, UserHandle u, boolean r) { appsChanged(); }
            @Override public void onPackagesUnavailable(String[] p, UserHandle u, boolean r) { appsChanged(); }
        };
        la.registerCallback(appsCallback, handler);
        Badges.listener = this::refreshBadges;
        if (Badges.allowed(this)) Media.start(this);

        wallpaperListener = (colors, which) -> {
            if ((which & WallpaperManager.FLAG_SYSTEM) == 0) return;
            boolean before = darkText;
            readWallpaperHints();
            if (before != darkText) bind();
        };
        WallpaperManager.getInstance(this).addOnColorsChangedListener(wallpaperListener, handler);

        tick = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (glance != null) glance.update();
            Media.refresh();
                refreshLive();
            }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(tick, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(tick, f);

        handleAuth(getIntent());
    }

    @Override protected void onStart() { super.onStart(); try { host.startListening(); } catch (Exception ignored) { } if (gfeed != null) gfeed.onStart(); }

    // ---------- the real Google feed (Discover) on the left, when the Google app allows it ----------
    GoogleFeed gfeed;

    boolean googleFeedReady() { return gfeed != null && gfeed.available(); }

    /** Google's Discover feed, in the Google app. */
    void openDiscover() {
        Intent i = getPackageManager().getLaunchIntentForPackage(GoogleFeed.GSA);
        if (i == null) { startSafely(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))); return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i, android.app.ActivityOptions.makeCustomAnimation(this, android.R.anim.slide_in_left, android.R.anim.fade_out).toBundle());
            workspace.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
        } catch (Exception e) { startSafely(i); }
    }

    private void startGoogleFeed() {
        gfeed = new GoogleFeed(this, new GoogleFeed.Listener() {
            @Override public void onFeedAvailable(boolean ok) { if (prefs.feed()) bind(); }
            @Override public void onFeedScroll(float p) {
                // Home slides and fades a little as the feed opens, like Pixel Launcher
                workspace.setTranslationX(p * U.dp(48));
                workspace.setAlpha(1f - p * 0.6f);
                bottom.setAlpha(Math.min(bottom.getAlpha(), 1f - p));
                if (p == 0f) updateBottom();
            }
        });
        workspace.overlay = new Workspace.Overlay() {
            // if Google won't share its feed with Horizon, the same swipe opens Discover in the Google app
            float pulled;
            boolean real;
            @Override public boolean ready() { return prefs.feed(); }
            @Override public void start() { real = googleFeedReady(); pulled = 0f; if (real) gfeed.startScroll(); }
            @Override public void scroll(float p) {
                if (real) { gfeed.scroll(p); return; }
                pulled = p;
                workspace.setTranslationX(Math.max(0f, p) * U.dp(48));
            }
            @Override public void end() {
                if (real) { gfeed.endScroll(); return; }
                workspace.animate().translationX(0f).setDuration(200).start();
                if (pulled > 0.18f) openDiscover();
            }
        };
        gfeed.connect();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (gfeed != null) gfeed.attach();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTilt();
        if (gfeed != null) gfeed.onPause();
    }

    private boolean motionAllowed() {
        try { return Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f; }
        catch (Exception e) { return true; }
    }

    private void startTilt() {
        if (!prefs.parallax() || !motionAllowed()) { stopTilt(); return; }
        if (sensors == null) sensors = getSystemService(android.hardware.SensorManager.class);
        android.hardware.Sensor s = sensors == null ? null : sensors.getDefaultSensor(android.hardware.Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (s == null && sensors != null) s = sensors.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR);
        if (s == null) return;
        tiltBase = null;
        sensors.registerListener(tilt, s, android.hardware.SensorManager.SENSOR_DELAY_GAME);
    }

    private boolean tiltOff;

    /** Pauses the tilt sensor while the drawer covers the wallpaper (no reset of the wallpaper position). */
    private void stopTiltQuiet() {
        if (sensors != null && !tiltOff) { sensors.unregisterListener(tilt); tiltOff = true; }
    }

    private void stopTilt() {
        if (sensors != null) sensors.unregisterListener(tilt);
        tiltX = tiltY = 0;
        applyTilt();
    }

    /** Tilt moves only the wallpaper; icons and widgets never move. */
    private void applyTilt() {
        if (workspace == null) return;
        int pages = Math.max(1, workspace.getChildCount() - 1);
        if (wallpaper.active()) wallpaper.move(tiltX, tiltY, U.clamp(pagePos / pages, 0f, 1f));
        else updateWallpaper();
    }
    @Override protected void onStop() { super.onStop(); try { host.stopListening(); } catch (Exception ignored) { } if (gfeed != null) gfeed.onStop(); }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs.sp.getBoolean("reloadStore", false)) { // a backup was restored
            prefs.sp.edit().remove("reloadStore").apply();
            store.load();
            prefStamp = -2;
        }
        if (prefs.stamp() != prefStamp && prefs.textPct() * 1000 + prefs.uiPct() != sizeKey) { recreate(); return; }
        Backup.daily(this);
        if (prefs.stamp() != prefStamp) onPrefsChanged();
        maybeImport();
        loadGlass();
        mark("resume:glass");
        if (glance != null) glance.update();
        refreshWeather(false);
        if (feed != null) feed.refresh();
        if (Badges.allowed(this)) Media.start(this);
        refreshBadges();
        updateSystemBars();
        startTilt();
        if (gfeed != null) gfeed.onResume();
    }

    @Override
    protected void onDestroy() {
        try { la.unregisterCallback(appsCallback); } catch (Exception ignored) { }
        try { unregisterReceiver(tick); } catch (Exception ignored) { }
        try { WallpaperManager.getInstance(this).removeOnColorsChangedListener(wallpaperListener); } catch (Exception ignored) { }
        Badges.listener = null;
        if (gfeed != null) gfeed.destroy(isChangingConfigurations());
        if (current == this) current = null;
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction()) && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (gfeed != null) gfeed.close();
            boolean alreadyHome = hasWindowFocus();
            if (!closeOverlays(alreadyHome) && alreadyHome) {
                if (workspace.current != homeIndex()) workspace.snapTo(homeIndex());
                else homeScroll.smoothScrollTo(0, 0);
            }
        }
        handleAuth(intent);
    }

    private void handleAuth(Intent i) {
        Uri u = i == null ? null : i.getData();
        if (u != null && "horizon".equals(u.getScheme())) {
            if (feed != null) feed.handleAuthRedirect(u);
            else U.toast(this, "Turn on the Now page in Horizon settings to use Microsoft sign-in.");
        }
    }

    @Override
    public void onBackPressed() {
        if (root.state != DragLayer.IDLE) return;
        if (allApps.progress() > 0f && allApps.back()) return;
        if (closeOverlays(true)) return;
        if (inlineFolder != null) { closeInlineFolder(); return; }
        if (feed != null && workspace.current == 0) {
            feed.back(r -> { if ("0".equals(r)) workspace.snapTo(homeIndex()); });
            return;
        }
        if (workspace.current != homeIndex()) { workspace.snapTo(homeIndex()); return; }
        if (homeScroll.getScrollY() > 0) { homeScroll.smoothScrollTo(0, 0); return; }
        if (!isDefaultHome()) super.onBackPressed();
    }

    /** Closes the topmost open layer; returns true if anything was open. */
    private boolean closeOverlays(boolean animate) {
        if (root.popup != null) { root.closePopup(); return true; }
        if (root.resize != null) { root.closeResize(); return true; }
        if (quick != null) { closeQuickMenu(); return true; }
        if (sheet != null) { closeSheet(); return true; }
        if (folder != null) { closeFolder(); return true; }
        if (allApps.progress() > 0f) { if (animate) allApps.close(); else allApps.setProgress(0f); return true; }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (feed != null) feed.onPermission(code, ok);
        if (glance != null) glance.update();
        if (code == REQ_LOC) refreshWeather(true);
        refreshLive();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (pendingWidget == null) return;
        if (req == REQ_BIND) { if (res == RESULT_OK) configureOrFinishWidget(); else cancelWidget(); }
        else if (req == REQ_CONFIG) { if (res == RESULT_OK) finishWidget(); else cancelWidget(); }
    }

    // ====================================================================== setup

    private static long T0;
    static void mark(String what) { android.util.Log.i("HorizonPerf", what + " @" + (android.os.SystemClock.uptimeMillis() - T0) + "ms"); }

    private long glassStamp = -1;

    /** With All files access, Horizon can read the current wallpaper itself; saves a copy for parallax and glass. */
    private boolean copyWallpaper(java.io.File f) {
        if (!android.os.Environment.isExternalStorageManager()) return false;
        try {
            android.graphics.drawable.Drawable d = WallpaperManager.getInstance(this).getDrawable();
            if (d == null) return false;
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            float k = Math.min(1f, Math.max(dm.widthPixels * 1.3f / Math.max(1, d.getIntrinsicWidth()), dm.heightPixels * 1.3f / Math.max(1, d.getIntrinsicHeight())));
            Bitmap b = U.bitmap(d, Math.max(1, Math.round(d.getIntrinsicWidth() * k)), Math.max(1, Math.round(d.getIntrinsicHeight() * k)));
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.JPEG, 92, o); }
            return true;
        } catch (Throwable e) { return false; }
    }

    /** One-time prompt: parallax and Liquid Glass need a copy of the wallpaper. */
    private void askForWallpaper() {
        prefs.sp.edit().putBoolean("wallAsked", true).apply();
        handler.postDelayed(() -> new android.app.AlertDialog.Builder(this, pal.dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Turn on wallpaper motion and glass")
                .setMessage("Android only lets Horizon move and blur a wallpaper it has a copy of. Pick your wallpaper image once and the wallpaper will move as you tilt the phone, and the glass will show it behind every card.")
                .setPositiveButton("Choose wallpaper", (dd, w) -> startSafely(new Intent(this, SettingsActivity.class).putExtra("chooseWallpaper", true)))
                .setNegativeButton("Not now", null)
                .show(), 800);
    }

    /** (Re)loads the blurred wallpaper copy used by the Spatial look's glass, when it changes. */
    private void loadGlass() {
        java.io.File f = WallpaperView.file(this);
        if (!f.exists() && copyWallpaper(f)) { prefs.put("parallax", true); onPrefsChanged(); }
        else if (!f.exists() && prefs.parallax() && !prefs.sp.getBoolean("wallAsked", false)) askForWallpaper();
        long st = f.exists() ? f.lastModified() : 0;
        if (st == glassStamp) return;
        glassStamp = st;
        android.graphics.Rect wb = getWindowManager().getCurrentWindowMetrics().getBounds();
        Glass.load(this, wb.width(), wb.height());
        Glass.invalidateAll();
    }

    private void applyPrefs() {
        prefStamp = prefs.stamp();
        cols = prefs.cols();
        iconPx = U.dp(56 * prefs.iconScale());
        apps.iconPx = Math.round(iconPx * 1.16f); // rendered at dock size so nothing is upscaled
        apps.themed = prefs.themed();
        Apps.tintAll = prefs.themedAll();
        Apps.shape = prefs.iconShape();
        pal = U.pal(this);
    }

    private java.util.Map<String, ?> lastPrefs;
    /** Settings that never change how Home is drawn: applied without rebuilding every tile and widget. */
    private static final java.util.Set<String> LIGHT = new java.util.HashSet<>(java.util.Arrays.asList(
            "stamp", "swipeAction", "doubleTapAction", "drawerKeyboard", "autoAdd", "locked", "units", "statusBar",
            "folderStyle", "drawerCols", "hidden", "parallaxAmt", "zone2", "npOutput", "npExtras", "npArt", "wxAnimated", "clockWeight", "dateLong", "clockEvents", "clockZone2", "wxTown", "wxHiLo", "wallAsked", "contactsAsked", "filesAsked", "smsAsked",
            "reloadStore", "tidy", "previews", "wxPlace"));

    /** True when only "light" settings changed since the last apply; those are handled without a full rebuild. */
    private boolean lightChange() {
        java.util.Map<String, ?> now = new java.util.HashMap<>(prefs.sp.getAll());
        java.util.Map<String, ?> before = lastPrefs;
        lastPrefs = now;
        if (before == null) return false;
        java.util.Set<String> keys = new java.util.HashSet<>(now.keySet());
        keys.addAll(before.keySet());
        boolean any = false;
        for (String k : keys) {
            if (java.util.Objects.equals(now.get(k), before.get(k))) continue;
            if (k.startsWith("stackShown") || k.startsWith("panelName")) continue;
            if (!LIGHT.contains(k)) return false;
            any = true;
        }
        return any || true;
    }

    private void onPrefsChanged() {
        if (lightChange()) {
            prefStamp = prefs.stamp();
            wallpaper.setStrength(prefs.sp.getInt("parallaxAmt", 35) / 100f);
            if (glance != null) glance.update();
            if (prefs.sp.getBoolean("tidy", false)) { prefs.sp.edit().remove("tidy").apply(); anchorHome(); }
            wxAt = 0;
            refreshWeather(false); // units may have changed
            allApps.restyle();
            updateSystemBars();
            return;
        }
        int oldCols = cols;
        applyPrefs();
        apps.clearIcons();
        shortcutIcons.clear();
        folderIcons.clear();
        tileColors.clear();
        wxAt = 0; // units or look may have changed
        if (oldCols != cols) reflow(oldCols);
        if (prefs.sp.getBoolean("tidy", false)) { prefs.sp.edit().remove("tidy").apply(); home.post(this::anchorHome); }
        fixDock();
        ensureGlance();
        if (root.bar != null) root.bar.hide();
        bind();
        allApps.restyle();
        if (feed != null) feed.setLook(pal);
        if (quick != null) { quick.setBackgroundColor(pal.scrim); quick.build(); }
        updateSystemBars();
    }

    /** Switches the look (Material You, Glass, Spatial, Modern Android, Tiles) and/or mode right away. */
    void setLook(String style, String mode) {
        if (style != null) prefs.put("style", style);
        if (mode != null) prefs.put("mode", mode);
        onPrefsChanged();
    }

    private void readWallpaperHints() {
        try {
            WallpaperColors wc = WallpaperManager.getInstance(this).getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            darkText = wc != null && (wc.getColorHints() & WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0;
        } catch (Exception e) {
            darkText = false;
        }
    }

    private void buildViews() {
        root = new DragLayer(this);
        wallpaper = new WallpaperView(this);
        root.addView(wallpaper, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        workspace = new Workspace(this);
        workspace.listener = this;
        root.addView(workspace, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Home never scrolls (only while a folder is open in place and pushes tiles down)
        homeScroll = new ScrollView(this) {
            @Override public boolean onInterceptTouchEvent(android.view.MotionEvent e) { return inlineFolder != null && super.onInterceptTouchEvent(e); }
            @Override public boolean onTouchEvent(android.view.MotionEvent e) { return inlineFolder != null && super.onTouchEvent(e); }
        };
        homeScroll.setVerticalScrollBarEnabled(false);
        homeScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        homeScroll.setClipChildren(false);
        home = new CellLayout(this, Item.DESKTOP, 0);
        home.tiles = true;
        home.setGrid(cols, 10);
        home.fitScreen = true;
        // only once the screen has settled: during start-up the grid briefly measures too few rows, and acting on
        // that moved (and saved) your icons on every restart
        home.onOverflow = () -> { handler.removeCallbacks(settledOverflow); handler.postDelayed(settledOverflow, 2000); };
        homeScroll.addView(home, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        homeScroll.setOnScrollChangeListener((v, x, y, ox, oy) -> updateWallpaper());
        homeScroll.addOnLayoutChangeListener((v, a, b, c, d, oa, ob, oc, od) -> { if (d - b != od - ob) home.requestLayout(); });

        bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        indicator = new PageIndicator(this);
        // page dots sit in their own band with equal space above and below
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, U.dp(16));
        ilp.setMargins(0, U.dp(2), 0, U.dp(16)); // lifted clear of the search bar
        bottom.addView(indicator, ilp);
        // one glass tray holds the search bar and the dock, its corners concentric with the bar inside it
        tray = new LinearLayout(this);
        tray.setOrientation(LinearLayout.VERTICAL);
        tray.setPadding(U.dp(8), U.dp(8), U.dp(8), U.dp(4));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(U.dp(10), U.dp(2), U.dp(10), U.dp(8));
        bottom.addView(tray, tlp);
        searchBar = makeSearchBar();
        tray.addView(searchBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, U.dp(56)));
        hotseat = new CellLayout(this, Item.HOTSEAT, 0);
        hotseat.setGrid(4, 1);
        hotseat.setPadding(U.dp(8), 0, U.dp(8), 0);
        tray.addView(hotseat, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, U.dp(88)));
        root.addView(bottom, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        root.addView(root.bar, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        allApps = new AllAppsView(this);
        root.addView(allApps, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        root.setOnApplyWindowInsetsListener((v, ins) -> {
            android.graphics.Insets s = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            insetTop = s.top;
            insetBottom = s.bottom;
            bottom.setPadding(0, 0, 0, insetBottom);
            bottom.setBackground(null);
            root.bar.setPadding(U.dp(12), insetTop + U.dp(10), U.dp(12), U.dp(10));
            allApps.setInsets(insetTop, insetBottom);
            allApps.setIme(ins.getInsets(WindowInsets.Type.ime()).bottom);
            if (feed != null) feed.setInsets(insetTop, insetBottom);
            applyPadding();
            return ins;
        });
    }

    /** One UI-style search bar: Search on the left; Galaxy AI, voice and camera search on the right. */
    private LinearLayout makeSearchBar() {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(U.dp(18), 0, U.dp(8), 0);
        b.setClickable(true);
        b.addView(new ImageView(this), new LinearLayout.LayoutParams(U.dp(20), U.dp(20)));
        TextView t = U.text(this, 16, 0, U.REGULAR);
        t.setText("Search");
        t.setPadding(U.dp(10), 0, 0, 0);
        b.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        b.addView(searchAction("Ask Claude", () -> askClaude(null)));
        b.addView(searchAction("Voice search", this::voiceSearch));
        b.setOnClickListener(v -> allApps.open(true));
        b.setContentDescription("Search apps and web");
        return b;
    }

    private ImageView searchAction(String desc, Runnable r) {
        ImageView iv = new ImageView(this);
        iv.setPadding(U.dp(10), U.dp(10), U.dp(10), U.dp(10));
        iv.setContentDescription(desc);
        iv.setOnClickListener(v -> r.run());
        iv.setLayoutParams(new LinearLayout.LayoutParams(U.dp(44), U.dp(44)));
        return iv;
    }

    /** Galaxy AI: the phone's assistant (Gemini or Bixby, whichever is set as default). */
    static final String CLAUDE = "com.anthropic.claude";

    /** The Claude app's own icon (full colour), or the AI sparkle if Claude isn't installed. */
    android.graphics.drawable.Drawable claudeIcon() {
        Apps.App c = apps.first(CLAUDE);
        if (c == null) return new AiSpark();
        // always Claude's real colours, whatever the icon style
        android.graphics.drawable.Drawable d;
        try { d = c.info.getIcon(getResources().getDisplayMetrics().densityDpi); } catch (Exception e) { return new AiSpark(); }
        return new android.graphics.drawable.BitmapDrawable(getResources(), Apps.render(this, d, apps.iconPx, false));
    }

    /** Opens Claude; with text, hands it to Claude as a new message. */
    void askClaude(String q) {
        Apps.App c = apps.first(CLAUDE);
        if (c == null) { if (q == null || q.isEmpty()) openAssistant(); else aiSearch(q); return; }
        if (q != null && !q.isEmpty()) {
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, q).setPackage(CLAUDE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(getPackageManager()) != null) { startSafely(i); allApps.setProgress(0f); return; }
        }
        launchApp(c, searchBar);
    }

    void openAssistantPublic() { openAssistant(); allApps.setProgress(0f); }

    private void openAssistant() {
        Intent i = new Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (i.resolveActivity(getPackageManager()) == null) {
            Apps.App g = apps.first("com.google.android.apps.bard");
            if (g == null) g = apps.first("com.samsung.android.bixby.agent");
            if (g != null) { launchApp(g, searchBar); return; }
            i = new Intent(Intent.ACTION_VOICE_COMMAND);
        }
        startSafely(i);
    }

    /** Camera search with Google Lens; falls back to the Google app. */
    private void cameraSearch() {
        Intent lens = new Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://lens")).setPackage("com.google.android.googlequicksearchbox");
        if (lens.resolveActivity(getPackageManager()) != null) { startSafely(lens); return; }
        Apps.App a = apps.first("com.google.ar.lens");
        if (a != null) { launchApp(a, searchBar); return; }
        openGoogleApp();
    }

    private void styleSearchBar() {
        // frosted glass, like iOS: the Search pill and the dock share one material
        boolean spatial = "spatial".equals(pal.style);
        int glass = spatial ? pal.surface : darkText ? U.alpha(0xffffffff, 0.42f) : U.alpha(0xffffffff, 0.16f);
        int ink = darkText ? 0xff1c1c1e : 0xffffffff;
        tray.setBackground(U.round(glass, U.R_CARD + 8)); // tray corner = bar corner + the 8dp inset: concentric
        searchBar.setBackground(U.round(darkText ? U.alpha(0xffffffff, 0.5f) : U.alpha(0xffffffff, 0.10f), U.R_CARD));
        ((ImageView) searchBar.getChildAt(0)).setImageDrawable(new Glyph(Glyph.SEARCH, ink));
        ((TextView) searchBar.getChildAt(1)).setTextColor(U.alpha(ink, 0.75f));
        ((ImageView) searchBar.getChildAt(2)).setImageDrawable(claudeIcon());
        ((ImageView) searchBar.getChildAt(3)).setImageDrawable(new Glyph(Glyph.MIC, ink));
        hotseat.setBackground(null);
    }

    private int bottomHeight() {
        return U.dp(34) + U.dp(22) + (prefs.searchBar() ? U.dp(56) : 0) + U.dp(88) + insetBottom;
    }

    private void applyPadding() {
        int side = U.margin() - U.gap() / 2;
        home.setPadding(side, insetTop + U.dp(8), side, U.dp(16));
        home.gap = U.gap();
        for (CellLayout g : extraHomes) { g.setPadding(side, insetTop + U.dp(8), side, U.dp(16)); g.gap = U.gap(); }
        for (FrameLayout w : extraWraps) w.setPadding(0, 0, 0, bottomHeight());
        // tiles stop above the search bar and dock instead of scrolling underneath them
        homeScroll.setPadding(0, 0, 0, bottomHeight());
        homeScroll.setClipToPadding(true);
        for (WidgetPanel p : panels) p.setInsets(insetTop, insetBottom);
    }

    int homeIndex() { return workspace.firstHomePage(); }

    // ---------- more pages of apps (screen 1, 2, ... to the right of Home) ----------
    final List<CellLayout> extraHomes = new ArrayList<>();
    private final List<FrameLayout> extraWraps = new ArrayList<>();

    int lastHomeIndex() { return homeIndex() + extraHomes.size(); }

    boolean isHomePage(int page) { return page >= homeIndex() && page <= lastHomeIndex(); }

    /** The app grid for a home screen number (0 = the main Home page). */
    CellLayout homeAt(int screen) {
        if (screen <= 0) return home;
        while (extraHomes.size() < screen) addHomePage();
        return extraHomes.get(screen - 1);
    }

    /** The app grid of the page you're looking at, or null on Now / widget pages. */
    CellLayout curHome() {
        int p = workspace.current;
        return isHomePage(p) ? homeAt(p - homeIndex()) : null;
    }

    private CellLayout addHomePage() {
        int screen = extraHomes.size() + 1;
        CellLayout g = new CellLayout(this, Item.DESKTOP, screen);
        g.tiles = true;
        g.setGrid(cols, 10);
        g.aspect = home.aspect;
        g.fitScreen = true;
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(g, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        int side = U.margin() - U.gap() / 2;
        g.setPadding(side, insetTop + U.dp(8), side, U.dp(16));
        g.gap = U.gap();
        wrap.setPadding(0, 0, 0, bottomHeight());
        wrap.setClipToPadding(true);
        extraHomes.add(g);
        extraWraps.add(wrap);
        workspace.addView(wrap, lastHomeIndex());
        indicator.set(workspace.getChildCount(), workspace.feed != null);
        return g;
    }

    // ====================================================================== binding

    void bind() {
        root.closePopup();
        root.closeResize();
        if (folder != null) closeFolder();
        quietCloseInline();
        int keepRel = workspace.getChildCount() > 0 ? workspace.current - homeIndex() : 0;
        views.clear();
        glance = null;
        workspace.removeAllViews();
        hotseat.removeAllViews();
        home.removeAllViews();

        if (false) { // Horizon's own left page is retired; the swipe opens Google's Discover instead
            if (feed == null) { feed = new FeedView(this); feed.setInsets(insetTop, insetBottom); }
            detach(feed);
            workspace.addView(feed);
            workspace.feed = feed;
            feed.setLook(pal);
        } else {
            workspace.feed = null;
            feed = null;
        }
        detach(homeScroll);
        workspace.addView(homeScroll);
        extraHomes.clear();
        extraWraps.clear();
        store.compact();
        for (int s = 1; s < store.screens(); s++) addHomePage();
        panels.clear();
        int pages = prefs.panelPages();
        for (Item i : store.items) if (i.container == Item.PANEL) pages = Math.max(pages, i.screen);
        for (int p = 0; p < pages; p++) {
            WidgetPanel wp = new WidgetPanel(this, p);
            wp.setInsets(insetTop, insetBottom);
            panels.add(wp);
            workspace.addView(wp);
        }

        home.setGrid(cols, 10);
        home.aspect = cols >= 6 ? 1.22f : 1.14f;
        hotseat.setGrid(prefs.dockCount(), 1);
        boolean own = wallpaper.load(prefs.parallax());
        wallpaper.setStrength(prefs.sp.getInt("parallaxAmt", 35) / 100f);
        wallpaper.setDim(prefs.dim());
        root.setBackgroundColor(own ? 0 : U.alpha(0xff000000, prefs.dim() / 100f));
        for (Item i : new ArrayList<>(store.items)) {
            if (i.container == Item.DESKTOP) addItemView(homeAt(i.screen), i);
            else if (i.container == Item.HOTSEAT) addItemView(hotseat, i);
        }
        // Home first; widget pages (live widgets are the slowest thing to build) right after the first frame
        final List<WidgetPanel> later = new ArrayList<>(panels);
        root.post(() -> { for (WidgetPanel p : later) if (panels.contains(p)) p.rebuild(); });
        searchBar.setVisibility(prefs.searchBar() ? View.VISIBLE : View.GONE);
        styleSearchBar();
        indicator.setColor(darkText ? 0xff1b1d22 : 0xffffffff);
        indicator.set(workspace.getChildCount(), workspace.feed != null);
        applyPadding();
        int target = Math.max(0, Math.min(workspace.getChildCount() - 1, homeIndex() + keepRel));
        workspace.setCurrentImmediate(target);
        refreshBadges();
        root.post(this::updateWallpaper);
    }

    private void addItemView(CellLayout cl, Item i) {
        View v = createView(i);
        if (v == null) return;
        cl.addView(v, new CellLayout.LP(i.x, i.y, i.spanX, i.spanY));
        views.put(i.id, v);
    }

    View createView(Item i) {
        View v;
        boolean dock = i.container == Item.HOTSEAT;
        switch (i.type) {
            case Item.APP:
            case Item.SHORTCUT:
            case Item.FOLDER: {
                if (i.type == Item.APP && apps.get(i.key()) == null) return null;
                if (dock) {
                    IconView iv = new IconView(this, Math.round(iconPx * 1.16f));
                    iv.showLabel = false;
                    iv.bind(i, iconFor(i), labelFor(i));
                    v = iv;
                } else {
                    TileView t = new TileView(this);
                    t.showLabel = prefs.tileLabels();
                    t.setWall(darkText);
                    Bitmap ic = iconFor(i);
                    t.bind(i, ic, labelFor(i), pal, tileColor(i, ic));
                    if (i.type == Item.FOLDER) t.setMinis(minis(i));
                    v = t;
                }
                v.setOnClickListener(this::onItemClick);
                break;
            }
            case Item.WIDGET:
                v = widgetView(i);
                break;
            case Item.STACK:
                v = stackView(i);
                break;
            case Item.GLANCE:
                glance = new GlanceView(this);
                v = glance;
                handler.post(() -> refreshWeather(false));
                break;
            case Item.MEDIA:
                v = new NowPlayingCard(this);
                break;
            default:
                return null;
        }
        v.setTag(i);
        v.setOnLongClickListener(this::onItemLongClick);
        return v;
    }

    private Context widgetCtx;

    /** Widgets draw with the phone's own text and display size (not Horizon's extra scaling), like on One UI. */
    private Context widgetContext() {
        if (widgetCtx == null) {
            android.content.res.Configuration sys = new android.content.res.Configuration(getApplicationContext().getResources().getConfiguration());
            widgetCtx = new android.view.ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault);
            ((android.view.ContextThemeWrapper) widgetCtx).applyOverrideConfiguration(sys);
        }
        return widgetCtx;
    }

    private View widgetView(Item i) {
        WidgetFrame f = new WidgetFrame(this, i);
        AppWidgetProviderInfo info = i.widgetId >= 0 ? awm.getAppWidgetInfo(i.widgetId) : null;
        if (info == null) f.setPlaceholder("This widget isn't available. Long-press to remove it.", pal);
        else {
            AppWidgetHostView hv = host.createView(this, i.widgetId, info);
            f.setWidget(hv, info);
        }
        return f;
    }

    /** Several widgets on one card; swipe sideways to switch. */
    private View stackView(Item s) {
        List<View> kids = new ArrayList<>();
        for (Item c : store.children(s.id)) {
            View w;
            if (c.type == Item.GLANCE) {
                glance = new GlanceView(this);
                handler.post(() -> refreshWeather(false));
                w = glance;
            } else if (c.type == Item.MEDIA) {
                w = new NowPlayingCard(this);
            } else w = widgetView(c);
            w.setTag(c);
            kids.add(w);
        }
        return new StackView(this, s, kids);
    }

    boolean touchInStack(float rx, float ry) {
        CellLayout g = curHome() != null ? curHome() : onPanelPage() ? currentPanel().grid : null;
        if (g == null) return false;
        int[] o = new int[2];
        for (int k = 0; k < g.getChildCount(); k++) {
            View v = g.getChildAt(k);
            if (!(v instanceof StackView) || ((StackView) v).count() < 2) continue;
            v.getLocationOnScreen(o);
            if (rx >= o[0] && ry >= o[1] && rx < o[0] + v.getWidth() && ry < o[1] + v.getHeight()) return true;
        }
        return false;
    }

    /** While dragging a widget, flips between widget pages; returns true if the page changed. */
    boolean dragFlip(Item it, int dir) {
        if (it.container != Item.PANEL && isHomePage(workspace.current) && workspace.settled()) {
            int to = workspace.current + dir;
            if (to < homeIndex()) return false;
            if (to > lastHomeIndex()) {
                // past the last page: make a new one (dropped there, it stays; left empty, it goes away)
                boolean hasItems = false;
                CellLayout last = homeAt(extraHomes.size());
                for (Item i : store.items) if (i.container == Item.DESKTOP && i.screen == last.screen && i != it) { hasItems = true; break; }
                if (!hasItems) return false;
                addHomePage();
                to = lastHomeIndex();
            }
            workspace.snapTo(to);
            return true;
        }
        if (it.container != Item.PANEL || !onPanelPage()) return false;
        int first = firstPanelIndex(), last = first + panels.size() - 1, to = workspace.current + dir;
        if (to < first || to > last) return false;
        workspace.snapTo(to);
        return true;
    }

    /** Moves a widget card to another widget page, into the first free spot. */
    private void moveToPage(Item it, int page) {
        it.screen = page + 1;
        int[] at = store.findFree(Item.PANEL, it.screen, 4, 400, it.spanX, it.spanY, it);
        it.x = at != null ? at[0] : 0;
        it.y = at != null ? at[1] : 0;
        store.save();
        reloadLayouts();
        workspace.snapTo(firstPanelIndex() + page);
    }

    private String pageName(int page) {
        String n = prefs.sp.getString("panelName" + page, "");
        return n.trim().isEmpty() ? (page == 0 ? "Widgets" : "Widgets " + (page + 1)) : n;
    }

    /** Stack actions, shown with the resize handles after a long-press on a widget or stack. */
    void showStackMenu(View v) {
        Item i = (Item) v.getTag();
        if (i == null || prefs.locked()) return;
        List<Object[]> rows = new ArrayList<>();
        // Horizon's own cards: their settings live in Horizon Settings
        if (i.type == Item.GLANCE || i.type == Item.MEDIA) {
            String section = i.type == Item.GLANCE ? "Horizon Now" : "Now Playing card";
            rows.add(new Object[]{Glyph.SETTINGS, "Edit widget", (Runnable) () -> { root.closeResize(); startSafely(new Intent(this, SettingsActivity.class).putExtra("section", section)); }});
            rows.add(new Object[]{Glyph.WIDGETS, "Stack a widget here", (Runnable) () -> { root.closeResize(); stackTarget = i.id; openWidgets(null, i.container); }});
            rows.add(new Object[]{Glyph.REMOVE, "Remove from Home", (Runnable) () -> { root.closeResize(); if (i.type == Item.GLANCE) prefs.sp.edit().putBoolean("glance", false).apply(); removeItem(i); }});
        }
        Item ed0 = i.type == Item.WIDGET ? i : v instanceof StackView ? ((StackView) v).current() : null;
        if (ed0 != null && (ed0.type == Item.GLANCE || ed0.type == Item.MEDIA)) {
            // a Horizon card at the front of a stack: edit opens its settings
            String section = ed0.type == Item.GLANCE ? "Horizon Now" : "Now Playing card";
            rows.add(new Object[]{Glyph.SETTINGS, "Edit widget", (Runnable) () -> { root.closeResize(); startSafely(new Intent(this, SettingsActivity.class).putExtra("section", section)); }});
        }
        final Item ed = ed0 != null && ed0.type == Item.WIDGET ? ed0 : null;
        if (ed != null && canEditWidget(ed)) rows.add(new Object[]{Glyph.SETTINGS, "Edit widget", (Runnable) () -> { root.closeResize(); editWidget(ed); }});
        if (i.type == Item.WIDGET) {
            rows.add(new Object[]{Glyph.WIDGETS, "Stack a widget here", (Runnable) () -> { root.closeResize(); stackTarget = i.id; openWidgets(null, i.container); }});
        } else if (v instanceof StackView) {
            StackView sv = (StackView) v;
            Item c = sv.current();
            rows.add(new Object[]{Glyph.PLUS, "Add a widget to this stack", (Runnable) () -> { root.closeResize(); stackTarget = i.id; openWidgets(null, i.container); }});
            if (c != null) {
                rows.add(new Object[]{Glyph.UP, "Take this widget out", (Runnable) () -> { root.closeResize(); unstack(i, c, false); }});
                rows.add(new Object[]{Glyph.REMOVE, "Remove this widget", (Runnable) () -> { root.closeResize(); unstack(i, c, true); }});
            }
        }
        if (i.container == Item.PANEL && panels.size() > 1) {
            for (int p = 0; p < panels.size(); p++) {
                if (p + 1 == i.screen) continue;
                final int to = p;
                rows.add(new Object[]{Glyph.NEXT, "Move to " + pageName(p), (Runnable) () -> { root.closeResize(); moveToPage(i, to); }});
            }
        }
        if (rows.isEmpty()) return;
        Rect r = U.rectIn(v, root);
        root.showPopup(new Popup(this, rows.toArray(new Object[0][])), new Rect(r.left, r.bottom - U.dp(4), r.right, r.bottom + U.dp(40)));
    }

    private boolean canEditWidget(Item w) {
        AppWidgetProviderInfo info = w.widgetId >= 0 ? awm.getAppWidgetInfo(w.widgetId) : null;
        return info != null && info.configure != null && Build.VERSION.SDK_INT >= 28;
    }

    /** Re-opens the widget's own settings screen (calendar, list, style...). */
    private void editWidget(Item w) {
        try {
            host.startAppWidgetConfigureActivityForResult(this, w.widgetId, 0, REQ_EDIT, null);
        } catch (Exception e) {
            U.toast(this, "This widget has no settings to change");
        }
    }

    private static final int REQ_EDIT = 77;

    /** Widgets, stacks and Horizon's own cards can all be dropped onto each other to make a stack. */
    private static boolean stackable(Item i) {
        return i.type == Item.WIDGET || i.type == Item.STACK || i.type == Item.GLANCE || i.type == Item.MEDIA;
    }

    /** Put a widget (or every widget of a dragged stack) into a target widget or stack. */
    private void stackInto(Item target, Item it) {
        Item s = target;
        if (target.type != Item.STACK) {
            s = new Item();
            s.type = Item.STACK;
            s.container = target.container;
            s.screen = target.screen;
            s.x = target.x; s.y = target.y; s.spanX = target.spanX; s.spanY = target.spanY;
            store.add(s);
            target.container = s.id;
            target.x = 0; target.y = 0;
        }
        int n = store.children(s.id).size();
        List<Item> moving = new ArrayList<>();
        if (it.type == Item.STACK) { moving.addAll(store.children(it.id)); store.items.remove(it); }
        else moving.add(it);
        for (Item m : moving) { m.container = s.id; m.x = n++; m.y = 0; }
        store.save();
    }

    /** Takes a widget out of a stack (or deletes it); a stack left with one widget becomes that widget. */
    private void unstack(Item s, Item c, boolean delete) {
        if (delete) deleteItem(c);
        else {
            c.container = s.container;
            c.screen = s.screen;
            c.spanX = s.spanX;
            c.spanY = s.spanY;
            CellLayout g = layoutOf(s);
            int cols = g != null ? g.cols : cols();
            int[] at = store.findFree(s.container, s.screen, cols, 400, c.spanX, c.spanY, c);
            c.x = at != null ? at[0] : 0;
            c.y = at != null ? at[1] : 0;
        }
        List<Item> left = store.children(s.id);
        for (int k = 0; k < left.size(); k++) left.get(k).x = k;
        if (left.size() <= 1) {
            store.items.remove(s);
            for (Item r : left) {
                r.container = s.container; r.screen = s.screen;
                r.x = s.x; r.y = s.y; r.spanX = s.spanX; r.spanY = s.spanY;
            }
        }
        store.save();
        reloadLayouts();
    }

    private int cols() { return cols; }

    private void reloadLayouts() {
        for (WidgetPanel p : panels) p.rebuild();
        bind();
    }

    /** A card on the Widgets page: an app widget or the Now Playing card, clipped to the look's corners. */
    View createPanelView(Item i) {
        View v;
        if (i.type == Item.MEDIA) v = new NowPlayingCard(this);
        else if (i.type == Item.WIDGET) {
            v = widgetView(i);
        } else if (i.type == Item.STACK) {
            v = stackView(i);
        } else return null;
        v.setTag(i);
        v.setOnLongClickListener(this::onItemLongClick);
        views.put(i.id, v);
        return v;
    }

    Bitmap iconFor(Item i) {
        if (i.type == Item.APP) {
            Apps.App a = apps.get(i.key());
            return a != null ? apps.icon(a) : Apps.render(this, getPackageManager().getDefaultActivityIcon(), iconPx, false);
        }
        if (i.type == Item.SHORTCUT) return shortcutBitmap(i);
        if (i.type == Item.FOLDER) return folderBitmap(i);
        return null;
    }

    String labelFor(Item i) {
        if (i.type == Item.APP) { Apps.App a = apps.get(i.key()); return a != null ? a.label : i.title; }
        return i.title;
    }

    private Bitmap shortcutBitmap(Item i) {
        Bitmap b = shortcutIcons.get(i.id);
        if (b != null) return b;
        ShortcutInfo si = apps.pinned(i.pkg, i.shortcutId, apps.user(i.user));
        if (si != null) b = apps.shortcutIcon(si);
        else { Apps.App a = apps.first(i.pkg); b = a != null ? apps.icon(a) : Apps.render(this, getPackageManager().getDefaultActivityIcon(), iconPx, false); }
        shortcutIcons.put(i.id, b);
        return b;
    }

    private List<Bitmap> minis(Item f) {
        List<Bitmap> out = new ArrayList<>();
        for (Item k : store.children(f.id)) { Bitmap b = iconFor(k); if (b != null) out.add(b); }
        return out;
    }

    /** Dock folder icon: a soft circle with up to four app icons. */
    private final java.util.Map<String, Bitmap> folderIcons = new java.util.HashMap<>();

    private Bitmap folderBitmap(Item f) {
        StringBuilder key = new StringBuilder().append(f.id).append(':').append(iconPx).append(darkText).append(Sq.glassy).append(Apps.shape);
        for (Item k : store.children(f.id)) key.append(',').append(k.key()).append(k.shortcutId);
        Bitmap hit = folderIcons.get(key.toString());
        if (hit != null) return hit;
        Bitmap made = drawFolderBitmap(f);
        folderIcons.put(key.toString(), made);
        return made;
    }

    private Bitmap drawFolderBitmap(Item f) {
        int s = iconPx;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(darkText ? 0x66ffffff : 0x40ffffff); // frosted folder, iOS style
        c.drawCircle(s / 2f, s / 2f, s / 2f, p);
        if (Sq.glassy) {
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
            g.setShader(new android.graphics.LinearGradient(0, 0, 0, s * 0.6f, 0x33ffffff, 0x00ffffff, android.graphics.Shader.TileMode.CLAMP));
            c.drawCircle(s / 2f, s / 2f, s / 2f, g);
            g.setShader(new android.graphics.LinearGradient(0, 0, 0, s, 0x80ffffff, 0x1affffff, android.graphics.Shader.TileMode.CLAMP));
            g.setStyle(Paint.Style.STROKE);
            g.setStrokeWidth(U.dp(1));
            c.drawCircle(s / 2f, s / 2f, s / 2f - U.dp(0.5f), g);
        }
        p.setColor(0xffffffff); // full opacity for the app icons themselves
        List<Item> kids = store.children(f.id);
        float mini = s * 0.32f, gap = s * 0.07f, x0 = s / 2f - mini - gap / 2f, y0 = s / 2f - mini - gap / 2f;
        for (int k = 0; k < Math.min(4, kids.size()); k++) {
            Item kid = kids.get(k);
            Bitmap ib = kid.type == Item.FOLDER ? null : iconFor(kid);
            if (ib == null) continue;
            float x = x0 + (k % 2) * (mini + gap), y = y0 + (k / 2) * (mini + gap);
            c.drawBitmap(ib, null, new RectF(x, y, x + mini, y + mini), p);
        }
        return b;
    }

    /** Tile background: the look's tile color, or the app's own color in the Tiles look. */
    private int tileColor(Item i, Bitmap icon) {
        if (!pal.colorTiles) return pal.tile;
        if (i.type == Item.FOLDER || icon == null) return pal.tile;
        String k = i.key() + i.shortcutId;
        Integer c = tileColors.get(k);
        if (c == null) { c = dominant(icon, k); tileColors.put(k, c); }
        String op = prefs.tileOpacity();
        return "auto".equals(op) ? c : U.alpha(c, Integer.parseInt(op) / 100f);
    }

    /** Windows Phone accent colors, used when an icon has no single dominant hue. */
    private static final int[] METRO = {0xff0078d7, 0xffe81123, 0xff00a300, 0xffff8c00, 0xff9a0089,
            0xff00aba9, 0xffe3008c, 0xff7a7574, 0xff1e7145, 0xff603cba, 0xffda532c, 0xff2d89ef};

    /** The icon's main hue as a deep tile color; multicolor icons get a stable accent from METRO. */
    private static int dominant(Bitmap b, String key) {
        Bitmap s = Bitmap.createScaledBitmap(b, 16, 16, true);
        float[] hsv = new float[3], bucket = new float[12], sat = new float[12], val = new float[12];
        float total = 0;
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
            int c = s.getPixel(x, y);
            if (Color.alpha(c) < 200) continue;
            Color.colorToHSV(c, hsv);
            if (hsv[1] < 0.3f || hsv[2] < 0.2f) continue;
            int k = Math.min(11, (int) (hsv[0] / 30f));
            float w = hsv[1] * hsv[2];
            bucket[k] += w; sat[k] += hsv[1] * w; val[k] += hsv[2] * w;
            total += w;
        }
        int top = 0;
        for (int k = 1; k < 12; k++) if (bucket[k] > bucket[top]) top = k;
        if (total <= 0 || bucket[top] / total < 0.5f) return METRO[Math.abs(key.hashCode()) % METRO.length];
        float[] pick = {top * 30f + 15f, U.clamp(sat[top] / bucket[top], 0.55f, 0.9f), U.clamp(val[top] / bucket[top], 0.4f, 0.6f)};
        return Color.HSVToColor(pick);
    }

    void refreshFolderIcon(Item f) {
        View v = views.get(f.id);
        if (v instanceof TileView) {
            TileView t = (TileView) v;
            t.bind(f, null, f.title, pal, tileColor(f, null));
            t.setMinis(minis(f));
        } else if (v instanceof IconView) {
            ((IconView) v).bind(f, folderBitmap(f), f.title);
        }
        refreshBadges();
    }

    int badgeFor(Item i) {
        if (i.type == Item.FOLDER) {
            int n = 0;
            for (Item k : store.children(i.id)) n += badgeFor(k);
            return n;
        }
        return i.type == Item.APP || i.type == Item.SHORTCUT ? Badges.get(i.pkg, i.user) : 0;
    }

    void refreshBadges() {
        String mode = prefs.badges();
        for (View v : views.values()) {
            if (v instanceof IconView) ((IconView) v).setBadge(badgeFor((Item) v.getTag()), mode);
        }
        refreshLive();
        if (allApps != null) allApps.refreshBadges();
    }

    /** Live tiles: counts, latest notification line, and today's date with the next event on calendar tiles. */
    void refreshLive() {
        String mode = prefs.badges();
        boolean pv = prefs.previews();
        String cal = null, day = null, week = null;
        for (View v : views.values()) {
            if (!(v instanceof TileView)) continue;
            TileView t = (TileView) v;
            Item i = (Item) v.getTag();
            String text = pv && i.type != Item.FOLDER ? Badges.preview(i.pkg, i.user) : null;
            t.setLive(badgeFor(i), mode, text);
            if (i.type == Item.APP && CALENDARS.contains(i.pkg)) {
                if (day == null) {
                    Date now = new Date();
                    day = String.valueOf(DateFormat.format("d", now));
                    week = String.valueOf(DateFormat.format("EEEE", now));
                    cal = (String) GlanceView.next(this)[0];
                }
                t.setCalendar(day, week, cal);
            }
        }
    }

    // ====================================================================== clicks

    void onItemClick(View v) {
        Item i = (Item) v.getTag();
        if (i == null) return;
        switch (i.type) {
            case Item.APP: launchApp(apps.get(i.key()), v); break;
            case Item.SHORTCUT: startPinnedShortcut(i, v); break;
            case Item.FOLDER: {
                // medium and large folders open the tapped app straight away, like iOS large folders
                int k = v instanceof TileView ? ((TileView) v).miniAt() : -1;
                List<Item> kids = k >= 0 ? store.children(i.id) : null;
                if (kids != null && k < kids.size()) {
                    Item c = kids.get(k);
                    if (c.type == Item.APP) { launchApp(apps.get(c.key()), v); break; }
                    if (c.type == Item.SHORTCUT) { startPinnedShortcut(c, v); break; }
                }
                if (i.container == Item.DESKTOP && i.screen == 0 && "inline".equals(prefs.folderStyle())) toggleInlineFolder(i); else openFolder(i);
                break;
            }
        }
    }

    private Bundle launchOptions(View v) {
        return ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.getWidth(), v.getHeight()).toBundle(); // the app grows out of its icon
    }

    private Rect screenRect(View v) {
        int[] p = new int[2];
        v.getLocationOnScreen(p);
        return new Rect(p[0], p[1], p[0] + v.getWidth(), p[1] + v.getHeight());
    }

    void launchApp(Apps.App a, View v) {
        if (a == null) { U.toast(this, "That app isn't installed anymore"); return; }
        try {
            la.startMainActivity(a.cn, a.user, screenRect(v), launchOptions(v));
            Usage.count(this, a.key);
            if (allApps.progress() > 0f) handler.postDelayed(() -> allApps.setProgress(0f), 400);
            if (folder != null) handler.postDelayed(this::closeFolder, 300);
        } catch (Exception e) {
            U.toast(this, "Couldn't open " + a.label);
        }
    }

    void startShortcut(ShortcutInfo si, View v) {
        try { la.startShortcut(si, screenRect(v), launchOptions(v)); }
        catch (Exception e) { U.toast(this, "Couldn't open that shortcut"); }
    }

    private void startPinnedShortcut(Item i, View v) {
        try { la.startShortcut(i.pkg, i.shortcutId, screenRect(v), launchOptions(v), apps.user(i.user)); }
        catch (Exception e) { U.toast(this, "That shortcut isn't available. Long-press to remove it."); }
    }

    void startSafely(Intent i) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { U.toast(this, "No app can open this"); }
    }

    /** Asks AI about the query: Google AI Mode (Gemini), the same assistant Galaxy AI search uses. */
    void aiSearch(String q) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?udm=50&q=" + Uri.encode(q)));
        if (apps.first(GoogleFeed.GSA) != null) i.setPackage(GoogleFeed.GSA);
        if (i.resolveActivity(getPackageManager()) == null) i.setPackage(null);
        startSafely(i);
        handler.postDelayed(() -> allApps.setProgress(0f), 400);
    }

    /** Searches you've sent to the web, newest first (kept on the phone, up to 50). */
    java.util.List<String> searchHistory() {
        java.util.List<String> h = new java.util.ArrayList<>();
        for (String s : prefs.sp.getString("searchHist", "").split("\n")) if (!s.isEmpty()) h.add(s);
        return h;
    }

    void forgetSearch(String q) {
        java.util.List<String> h = searchHistory();
        h.removeIf(s -> s.equalsIgnoreCase(q));
        prefs.sp.edit().putString("searchHist", String.join("\n", h)).apply();
    }

    void webSearch(String q) {
        if (prefs.sp.getBoolean("searchHistOn", true) && !q.trim().isEmpty()) {
            java.util.List<String> h = searchHistory();
            h.removeIf(s -> s.equalsIgnoreCase(q.trim()));
            h.add(0, q.trim());
            while (h.size() > 50) h.remove(h.size() - 1);
            prefs.sp.edit().putString("searchHist", String.join("\n", h)).apply();
        }
        Intent i = new Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q);
        if (i.resolveActivity(getPackageManager()) == null)
            i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)));
        startSafely(i);
        handler.postDelayed(() -> allApps.setProgress(0f), 400);
    }

    private void voiceSearch() {
        Intent i = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
        if (i.resolveActivity(getPackageManager()) == null) i = new Intent(Intent.ACTION_VOICE_COMMAND);
        startSafely(i);
    }

    void openGoogleApp() {
        Intent i = getPackageManager().getLaunchIntentForPackage("com.google.android.googlequicksearchbox");
        if (i == null) U.toast(this, "The Google app isn't installed");
        else startSafely(i);
    }

    // ====================================================================== long-press and drag

    private boolean onItemLongClick(View v) {
        if (root.state != DragLayer.IDLE) return false;
        Item i = (Item) v.getTag();
        if (prefs.locked()) {
            if (i.isIcon()) root.showPopup(new Popup(this, i, DragLayer.FROM_HOME, v), U.rectIn(v, root));
            else U.toast(this, "Home layout is locked. Unlock it in Horizon settings.");
            return true;
        }
        if (v instanceof IconView) {
            IconView iv = (IconView) v;
            root.beginDrag(v, i, DragLayer.FROM_HOME, iv.icon, iv.iconRectIn(root));
            root.showPopup(new Popup(this, i, DragLayer.FROM_HOME, v), iv.iconRectIn(root));
        } else {
            Rect r = U.rectIn(v, root);
            root.beginDrag(v, i, DragLayer.FROM_HOME, U.snapshot(v), r);
            if (i.isIcon()) root.showPopup(new Popup(this, i, DragLayer.FROM_HOME, v), r);
        }
        return true;
    }

    boolean onDrawerLongClick(IconView v) {
        if (root.state != DragLayer.IDLE) return false;
        Item it = v.item.copy();
        it.spanX = 1;
        it.spanY = 1;
        if (!prefs.locked()) root.beginDrag(v, it, DragLayer.FROM_DRAWER, v.icon, v.iconRectIn(root));
        root.showPopup(new Popup(this, it, DragLayer.FROM_DRAWER, v), v.iconRectIn(root));
        return true;
    }

    boolean onFolderChildLongClick(IconView v) {
        if (root.state != DragLayer.IDLE) return false;
        if (!prefs.locked()) root.beginDrag(v, v.item, DragLayer.FROM_FOLDER, v.icon, v.iconRectIn(root));
        root.showPopup(new Popup(this, v.item, DragLayer.FROM_FOLDER, v), v.iconRectIn(root));
        return true;
    }

    void onDragStart(Item item, int from) {
        home.expand = true;
        home.requestLayout();
        for (WidgetPanel p : panels) { p.grid.expand = true; p.grid.requestLayout(); }
        if (from == DragLayer.FROM_DRAWER) allApps.animateTo(0f);
        // from a folder: it stays open so the app can be moved around inside it; leaving the card takes it out
        if (from == DragLayer.FROM_FOLDER) {
            folderDrag = folder != null ? folder : inlinePanel;
            folderOrder = new ArrayList<>(store.children(item.container));
            if (folderDrag == null) { closeFolder(); quietCloseInline(); }
        }
        if (from != DragLayer.FROM_HOME && from != DragLayer.FROM_FOLDER && !isHomePage(workspace.current)) workspace.snapTo(homeIndex());
        for (CellLayout g : extraHomes) { g.expand = true; g.requestLayout(); }
    }

    static void hover(View v, boolean on) {
        if (v instanceof IconView) ((IconView) v).setFolderHover(on);
        else if (v instanceof TileView) ((TileView) v).setFolderHover(on);
        else if (v instanceof WidgetFrame || v instanceof StackView || v instanceof GlanceView || v instanceof NowPlayingCard) {
            v.animate().scaleX(on ? 0.95f : 1f).scaleY(on ? 0.95f : 1f).alpha(on ? 0.75f : 1f).setDuration(120).start();
        }
    }

    boolean touchInWidget(float x, float y) {
        CellLayout g = curHome() != null ? curHome() : onPanelPage() ? currentPanel().grid : null;
        if (g == null) return false;
        for (int k = 0; k < g.getChildCount(); k++) {
            View v = g.getChildAt(k);
            if (v instanceof WidgetFrame && U.rectIn(v, root).contains((int) x, (int) y) && ((WidgetFrame) v).scrollable()) return true;
            if (v instanceof StackView && U.rectIn(v, root).contains((int) x, (int) y) && ((StackView) v).scrollable()) return true;
        }
        return false;
    }

    private boolean idle() {
        return root.state == DragLayer.IDLE && folder == null && sheet == null && quick == null
                && allApps.progress() == 0f && workspace.settled() && isHomePage(workspace.current);
    }

    /** The app drawer opens with a swipe up that starts on the dock or search bar (Home itself scrolls). */
    /** Home doesn't scroll, so a swipe up anywhere on it opens search (only the dock area while a folder is open). */
    boolean canSwipeUp(float y) {
        if (!idle()) return false;
        return inlineFolder == null || y >= root.getHeight() - bottom.getHeight() - U.dp(8);
    }

    boolean canSwipeDown() { return idle() && homeScroll.getScrollY() == 0; }

    void onSwipeDown() {
        switch (prefs.swipeAction()) {
            case "shade": expandShade(); break;
            case "search": allApps.open(true); break;
            case "none": break;
            default: showQuickMenu();
        }
    }

    boolean overHome(float x, float y) {
        return isHomePage(workspace.current) && y < root.getHeight() - bottom.getHeight();
    }

    int firstPanelIndex() { return lastHomeIndex() + 1; }

    boolean onPanelPage() { return workspace.settled() && workspace.current >= firstPanelIndex(); }

    /** The widget page on screen, or the first one when Home or Now is showing. */
    WidgetPanel currentPanel() {
        int p = workspace.current - firstPanelIndex();
        return panels.get(Math.max(0, Math.min(panels.size() - 1, p)));
    }

    private WidgetPanel panelOf(Item i) { return panels.get(Math.max(0, Math.min(panels.size() - 1, i.screen - 1))); }

    void addPanelPage() {
        int n = prefs.panelPages() + 1;
        prefs.sp.edit().putInt("panelPages", n).apply();
        bind();
        workspace.snapTo(firstPanelIndex() + n - 1);
    }

    void removePanelPage(int page) {
        if (page == 0 || !store.on(Item.PANEL, page + 1).isEmpty()) return;
        for (Item i : store.items) if (i.container == Item.PANEL && i.screen > page + 1) i.screen--;
        prefs.sp.edit().putInt("panelPages", Math.max(1, prefs.panelPages() - 1)).apply();
        store.save();
        bind();
        workspace.snapTo(firstPanelIndex() + Math.max(0, page - 1));
    }

    /** Over a page that scrolls while dragging (Home or the Widgets page). */
    boolean overPage(float x, float y) { return overHome(x, y) || onPanelPage(); }

    int homeTop() { return insetTop; }
    int homeBottom() { return onPanelPage() ? root.getHeight() : root.getHeight() - bottom.getHeight(); }

    boolean autoScroll(int dir) {
        ScrollView s = onPanelPage() ? currentPanel() : homeScroll;
        int before = s.getScrollY();
        s.scrollBy(0, dir * U.dp(14));
        return s.getScrollY() != before;
    }

    private int homeRowsFor(int sy) { return rowsFor(home, sy); }

    private int rowsFor(CellLayout g, int sy) {
        if (g.container == Item.DESKTOP && g.fitRows > 0) return g.fitRows; // Home pages fit the screen; nothing goes below
        int m = 0;
        for (Item i : store.on(g.container, g.screen)) m = Math.max(m, i.y + i.spanY);
        return Math.max(g.rows, m) + sy + 1;
    }

    private FolderView folderDrag;
    private List<Item> folderOrder;

    /** Dragged out of the folder's card: close it and carry on as a normal home-screen drag. */
    private void leaveFolderDrag() {
        if (folderDrag == null) return;
        folderDrag = null;
        folderOrder = null;
        closeFolder();
        quietCloseInline();
        if (!isHomePage(workspace.current)) workspace.snapTo(homeIndex());
    }

    DragLayer.Target findTarget(float x, float y, Item item, View src, float left, float top) {
        DragLayer.Target t = new DragLayer.Target();
        int b = root.bar.targetAt(x, y);
        if (b >= 0) { t.bar = b; return t; }
        if (folderDrag != null) {
            if (folderDrag.cardRect().contains((int) x, (int) y)) {
                int at = folderOrder.indexOf(item), slot = folderDrag.slotAt(x, y);
                if (slot >= 0 && slot < folderOrder.size() && slot != at && at >= 0) {
                    // the others shuffle over to make room, like any phone's folder
                    folderOrder.remove(at);
                    folderOrder.add(slot, item);
                    folderDrag.showOrder(folderOrder, item.id);
                    root.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                    at = slot;
                }
                t.folderSlot = Math.max(0, at);
                t.valid = true;
                return t;
            }
            leaveFolderDrag();
        }
        CellLayout cl = null;
        if (item.isIcon() && bottom.getAlpha() > 0.5f && U.rectIn(hotseat, root).contains((int) x, (int) y)) cl = hotseat;
        else if (item.container == Item.PANEL && onPanelPage()) cl = currentPanel().grid;
        else if (item.container != Item.PANEL && overHome(x, y)) cl = curHome();
        if (cl == null) return t;
        int sx = cl == hotseat ? 1 : item.spanX, sy = cl == hotseat ? 1 : item.spanY;
        Rect r = U.rectIn(cl, root);
        boolean one = sx == 1 && sy == 1;
        float px = one ? x - r.left : left + cl.cellW / 2f - r.left;
        float py = one ? y - r.top : top + cl.cellH / 2f - r.top;
        int[] c = cl.cellAt(px, py);
        t.layout = cl;
        if (item.type == Item.APP || item.type == Item.SHORTCUT) {
            int[] fc = cl.cellAt(x - r.left, y - r.top);
            View v = cl.childAt(fc[0], fc[1]);
            if (v != null && v != src && (v instanceof TileView || v instanceof IconView)) {
                Item ti = (Item) v.getTag();
                Rect vr = U.rectIn(v, root);
                float rad = Math.min(vr.width(), vr.height()) * 0.34f;
                if (ti.isIcon() && Math.hypot(x - vr.centerX(), y - vr.centerY()) < rad) {
                    t.merge = ti;
                    t.mergeView = v;
                    t.valid = true;
                    t.x = ti.x;
                    t.y = ti.y;
                    return t;
                }
            }
        }
        if (stackable(item) && cl != hotseat) {
            for (int k = 0; k < cl.getChildCount(); k++) {
                View v = cl.getChildAt(k);
                if (v == src || !(v instanceof WidgetFrame || v instanceof StackView || v instanceof GlanceView || v instanceof NowPlayingCard)) continue;
                Item ti = (Item) v.getTag();
                if (ti == null || ti.id == item.id) continue;
                Rect vr = U.rectIn(v, root);
                Rect core = new Rect(vr);
                core.inset(vr.width() / 4, vr.height() / 4);
                if (core.contains((int) x, (int) y)) {
                    t.merge = ti; t.mergeView = v; t.valid = true; t.x = ti.x; t.y = ti.y;
                    return t;
                }
            }
        }
        if (sx > cl.cols) return t;
        int rows = cl == hotseat ? 1 : rowsFor(cl, sy);
        int cx = Math.max(0, Math.min(c[0], cl.cols - sx)), cy = Math.max(0, Math.min(c[1], rows - sy));
        boolean[][] g = store.grid(cl.container, cl.screen, cl.cols, rows, item);
        if (Store.fits(g, cx, cy, sx, sy)) {
            t.x = cx; t.y = cy; t.valid = true;
        } else {
            int[] near = store.nearestFree(cl.container, cl.screen, cl.cols, rows, sx, sy, item, cx, cy);
            if (near != null) { t.x = near[0]; t.y = near[1]; t.valid = true; }
        }
        return t;
    }

    Rect onDrop(Item item, int from, View src, DragLayer.Target t) {
        Item parent = from == DragLayer.FROM_FOLDER ? store.byId(item.container) : null;
        if (folderDrag != null) {
            FolderView fd = folderDrag;
            if (t != null && t.folderSlot >= 0 && t.bar < 0) {
                // dropped inside the folder: keep the new order
                for (int k = 0; k < folderOrder.size(); k++) folderOrder.get(k).x = k;
                store.save();
                if (parent != null) refreshFolderIcon(parent);
                Rect r = fd.slotRect(t.folderSlot);
                return r;
            }
            if (t == null || t.bar < 0) {
                // let go somewhere that isn't a place: everything back as it was
                fd.showOrder(store.children(item.container), -1);
                folderDrag = null;
                folderOrder = null;
                return null;
            }
            // over Remove / App info / Uninstall: close the folder and let the bar handle it
            leaveFolderDrag();
        }
        if (t != null && t.bar == DropBar.REMOVE) {
            if (from == DragLayer.FROM_DRAWER) { restore(src); return null; }
            if (from == DragLayer.FROM_HOME) { views.remove(item.id); detach(src); }
            deleteItem(item);
            fixFolder(parent);
            store.save();
            return null;
        }
        if (t != null && t.bar == DropBar.INFO) { appInfo(item); return revert(from, src); }
        if (t != null && t.bar == DropBar.UNINSTALL) { uninstall(item); return revert(from, src); }
        if (t == null || t.layout == null || !t.valid) return revert(from, src);

        Rect mergeRect = t.mergeView != null ? U.rectIn(t.mergeView, root) : null;
        Item it = from == DragLayer.FROM_DRAWER ? store.add(item.copy()) : item;
        if (from == DragLayer.FROM_DRAWER) restore(src);
        long oldContainer = it.container;
        View view = from == DragLayer.FROM_HOME ? src : null;
        if (view != null) detach(view);
        Rect dest;
        if (t.merge != null && !t.merge.isIcon() && stackable(t.merge)) {
            if (view != null) views.remove(it.id);
            stackInto(t.merge, it);
            handler.post(this::reloadLayouts);
            return mergeRect;
        }
        if (t.merge != null) {
            Item target = t.merge;
            if (view != null) views.remove(it.id);
            if (target.type == Item.FOLDER) {
                it.container = -1;
                it.x = store.children(target.id).size();
                it.container = target.id;
                it.y = 0;
                it.spanX = it.spanY = 1;
                refreshFolderIcon(target);
            } else {
                Item f = new Item();
                f.type = Item.FOLDER;
                f.container = target.container;
                f.x = target.x;
                f.y = target.y;
                f.spanX = target.spanX;
                f.spanY = target.spanY;
                f.title = folderName(target, it);
                store.add(f);
                View tv = views.remove(target.id);
                detach(tv);
                target.container = f.id; target.x = 0; target.y = 0; target.spanX = target.spanY = 1;
                it.container = f.id; it.x = 1; it.y = 0; it.spanX = it.spanY = 1;
                addItemView(t.layout, f);
            }
            dest = mergeRect;
        } else {
            if (t.layout == hotseat) { it.spanX = 1; it.spanY = 1; }
            it.container = t.layout.container;
            it.screen = 0;
            it.x = t.x;
            it.y = t.y;
            it.screen = t.layout.screen;
            if (view == null || oldContainer != it.container) {
                if (view != null) views.remove(it.id);
                view = it.container == Item.PANEL ? createPanelView(it) : createView(it);
            } else {
                ((CellLayout.LP) view.getLayoutParams()).sx = it.spanX;
            }
            if (view == null) { store.remove(it); store.save(); return null; }
            views.put(it.id, view);
            t.layout.addView(view, new CellLayout.LP(it.x, it.y, it.spanX, it.spanY));
            view.setVisibility(View.INVISIBLE);
            dest = t.layout == hotseat ? iconDest(hotseat, it.x) : cellDest(t.layout, it);
        }
        fixFolder(parent);
        store.save();
        if (it.type == Item.SHORTCUT) apps.syncPins(store);
        refreshBadges();
        return dest;
    }

    void onDropDone(Item item) {
        if (folderDrag != null) { // show the moved app in its new spot
            folderDrag.showOrder(store.children(item.container), -1);
            folderDrag = null;
            folderOrder = null;
        }
        if (home.expand) { home.expand = false; home.requestLayout(); }
        for (CellLayout g : extraHomes) if (g.expand) { g.expand = false; g.requestLayout(); }
        boolean moved = store.compact();
        if (moved || extraHomes.size() != store.screens() - 1) { store.save(); handler.post(this::bind); } // empty pages disappear
        for (WidgetPanel p : panels) if (p.grid.expand) { p.grid.expand = false; p.grid.requestLayout(); }
        View v = views.get(item.id);
        if (v != null) v.setVisibility(View.VISIBLE);
    }

    private Rect revert(int from, View src) {
        if (from == DragLayer.FROM_HOME && src != null && src.getParent() != null) {
            return src instanceof IconView ? ((IconView) src).iconRectIn(root) : U.rectIn(src, root);
        }
        restore(src);
        return null;
    }

    private void restore(View v) { if (v != null) v.setVisibility(View.VISIBLE); }

    private Rect iconDest(CellLayout cl, int x) {
        Rect r = U.rectIn(cl, root), cell = cl.cellRect(x, 0, 1, 1);
        int cx = r.left + cell.centerX(), cy = r.top + cell.centerY();
        return new Rect(cx - iconPx / 2, cy - iconPx / 2, cx + iconPx / 2, cy + iconPx / 2);
    }

    private Rect cellDest(CellLayout cl, Item it) {
        Rect r = U.rectIn(cl, root), cell = cl.cellRect(it.x, it.y, it.spanX, it.spanY);
        cell.offset(r.left, r.top);
        return cell;
    }

    private static void detach(View v) {
        if (v != null && v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
    }

    private void deleteItem(Item i) {
        if (i.type == Item.WIDGET && i.widgetId >= 0) { try { host.deleteAppWidgetId(i.widgetId); } catch (Exception ignored) { } }
        if (i.type == Item.STACK) for (Item c : store.children(i.id)) if (c.widgetId >= 0) { try { host.deleteAppWidgetId(c.widgetId); } catch (Exception ignored) { } }
        store.remove(i);
    }

    /** Re-ranks a folder after a change; a folder left with one app turns back into that app. */
    private void fixFolder(Item f) {
        if (f == null || store.byId(f.id) == null) return;
        List<Item> kids = store.children(f.id);
        for (int k = 0; k < kids.size(); k++) kids.get(k).x = k;
        if (kids.size() > 1) { refreshFolderIcon(f); return; }
        CellLayout cl = layoutOf(f);
        View fv = views.remove(f.id);
        detach(fv);
        store.items.remove(f);
        if (kids.size() == 1) {
            Item c = kids.get(0);
            c.container = f.container; c.screen = 0; c.x = f.x; c.y = f.y;
            c.spanX = f.container == Item.HOTSEAT ? 1 : f.spanX;
            c.spanY = f.container == Item.HOTSEAT ? 1 : f.spanY;
            if (cl != null) addItemView(cl, c);
        }
    }

    private CellLayout layoutOf(Item i) {
        if (i.container == Item.HOTSEAT) return hotseat;
        if (i.container == Item.PANEL) return panelOf(i).grid;
        if (i.container == Item.DESKTOP) return homeAt(i.screen);
        return null;
    }

    private String folderName(Item a, Item b) {
        Apps.App x = apps.get(a.key()), y = apps.get(b.key());
        String n1 = x != null ? categoryName(x.category) : null, n2 = y != null ? categoryName(y.category) : null;
        if (n1 != null && n1.equals(n2)) return n1;
        return n1 != null ? n1 : n2 != null ? n2 : "Folder";
    }

    private static String categoryName(int c) {
        switch (c) {
            case 0: return "Games";
            case 1: case 2: case 3: return "Media";
            case 4: return "Social";
            case 5: return "News";
            case 6: return "Travel";
            case 7: return "Productivity";
            case 8: return "Tools";
            default: return null;
        }
    }

    /** Small / Medium / Wide / Large: keeps the tile in place if it fits, otherwise moves it to the nearest gap. */
    void resizeTile(Item i, int sx, int sy) {
        sx = Math.min(sx, cols);
        int rows = homeRowsFor(sy);
        boolean[][] g = store.grid(Item.DESKTOP, 0, cols, rows, i);
        int x = Math.min(i.x, cols - sx), y = i.y;
        if (!Store.fits(g, x, y, sx, sy)) {
            int[] n = store.nearestFree(Item.DESKTOP, 0, cols, rows, sx, sy, i, i.x, i.y);
            if (n == null) return;
            x = n[0];
            y = n[1];
        }
        i.x = x; i.y = y; i.spanX = sx; i.spanY = sy;
        View v = views.get(i.id);
        if (v != null) {
            CellLayout.LP lp = (CellLayout.LP) v.getLayoutParams();
            lp.x = x; lp.y = y; lp.sx = sx; lp.sy = sy;
            v.requestLayout();
            v.invalidate();
        }
        store.save();
    }

    // ====================================================================== popup actions

    void appInfo(Item i) {
        try {
            if (i.type == Item.APP) la.startAppDetailsActivity(ComponentName.unflattenFromString(i.cmp), apps.user(i.user), null, null);
            else startSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + i.pkg)));
        } catch (Exception e) {
            startSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + i.pkg)));
        }
    }

    boolean canUninstall(Item i) {
        Apps.App a = i.type == Item.APP ? apps.get(i.key()) : null;
        return a != null && !a.system && !a.work;
    }

    void uninstall(Item i) { startSafely(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + i.pkg))); }

    void removeItem(Item i) {
        if (inlineFolder != null && inlineFolder.id == i.id) quietCloseInline();
        View v = views.remove(i.id);
        detach(v);
        deleteItem(i);
        store.save();
    }

    void removeFromFolder(Item i) {
        Item parent = store.byId(i.container);
        closeFolder();
        quietCloseInline();
        i.container = Item.DESKTOP;
        i.spanX = i.spanY = 1;
        place(i, false);
        fixFolder(parent);
        store.save();
    }

    void hideApp(Item i) {
        Set<String> h = prefs.hidden();
        h.add(i.key());
        prefs.setHidden(h);
        prefStamp = prefs.stamp();
        allApps.filter();
        U.toast(this, "Hidden from the app list. Unhide it in Horizon settings.");
    }

    void addToHome(Item drawerItem) {
        Item it = drawerItem.copy();
        it.id = 0;
        it.container = Item.DESKTOP;
        place(it, true);
        allApps.close();
        U.toast(this, "Added to Home");
    }

    void pinShortcutToHome(ShortcutInfo si) {
        Item it = new Item();
        it.type = Item.SHORTCUT;
        it.pkg = si.getPackage();
        it.shortcutId = si.getId();
        it.user = apps.serial(si.getUserHandle());
        it.title = String.valueOf(si.getShortLabel() != null ? si.getShortLabel() : si.getLongLabel());
        place(it, true);
        apps.syncPins(store);
        U.toast(this, "Added “" + it.title + "” to Home");
    }

    /** Called by AddItemActivity when another app asks to pin a shortcut or widget. */
    void addPinnedItem(Item it) {
        place(it, true);
        apps.syncPins(store);
    }

    /** Applies a layout copied from another launcher (see Importer), once. */
    private void maybeImport() {
        java.io.File f = Importer.file(this);
        if (!f.exists()) return;
        try {
            Importer im = new Importer(this);
            im.apply(new org.json.JSONObject(Importer.read(f)));
            f.renameTo(new java.io.File(f.getParentFile(), "import-done.json"));
            onPrefsChanged();
            String msg = "Imported " + im.apps + " apps and " + im.folders + " folders from One UI";
            if (im.missing > 0) msg += ". Not installed here: " + String.join(", ", im.notFound);
            U.toast(this, msg);
        } catch (Exception e) {
            U.toast(this, "Couldn't read the imported layout");
        }
    }

    /** Restore list from an import: removes the entry for a package once one of its widgets is added. */
    void clearPendingWidget(String pkg) {
        try {
            org.json.JSONArray a = new org.json.JSONArray(prefs.sp.getString("pendingWidgets", "[]")), out = new org.json.JSONArray();
            for (int k = 0; k < a.length(); k++) if (!pkg.equals(a.getJSONObject(k).optString("pkg"))) out.put(a.getJSONObject(k));
            prefs.sp.edit().putString("pendingWidgets", out.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** Settings → Reset Home layout. Apps stay installed; widgets are released. */
    void resetLayout() {
        for (Item i : new ArrayList<>(store.items))
            if (i.type == Item.WIDGET && i.widgetId >= 0) { try { host.deleteAppWidgetId(i.widgetId); } catch (Exception ignored) { } }
        store.items.clear();
        store.nextId = 1;
        defaultLayout();
        bind();
    }

    /** Puts an item in the first gap on Home (top to bottom), shows it and scrolls to it. */
    private void place(Item it, boolean isNew) {
        it.container = Item.DESKTOP;
        it.screen = 0;
        it.spanX = Math.min(it.spanX, cols);
        int[] at = store.findFree(Item.DESKTOP, 0, cols, homeRowsFor(it.spanY), it.spanX, it.spanY, it);
        if (at == null) at = new int[]{0, homeRowsFor(0)};
        it.x = at[0];
        it.y = at[1];
        if (isNew) store.add(it);
        store.save();
        addItemView(home, it);
        workspace.snapTo(homeIndex());
        final int row = it.y;
        homeScroll.post(() -> homeScroll.smoothScrollTo(0, Math.max(0, home.getPaddingTop() + row * home.cellH - homeScroll.getHeight() / 3)));
        refreshBadges();
    }

    // ====================================================================== folders

    void toggleInlineFolder(Item f) {
        if (inlineFolder != null && inlineFolder.id == f.id) { closeInlineFolder(); return; }
        quietCloseInline();
        inlinePanel = new FolderPanel(this, f);
        inlineFolder = f;
        home.setPanel(inlinePanel, f.y + f.spanY);
        homeScroll.post(() -> {
            int top = inlinePanel.getTop(), bot = inlinePanel.getBottom(), vis = homeScroll.getHeight() - bottom.getHeight();
            int y = homeScroll.getScrollY();
            if (bot > y + vis) homeScroll.smoothScrollTo(0, Math.min(top - U.dp(80), bot - vis + U.dp(16)));
        });
    }

    void onEmptyTap() {
        if (inlineFolder != null) closeInlineFolder();
        else if (folder != null) closeFolder();
    }

    void closeInlineFolder() {
        quietCloseInline();
    }

    private void quietCloseInline() {
        if (inlinePanel != null) {
            inlinePanel.saveTitle();
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            if (imm != null) imm.hideSoftInputFromWindow(inlinePanel.getWindowToken(), 0);
        }
        home.clearPanel();
        inlinePanel = null;
        inlineFolder = null;
    }

    void openFolder(Item f) {
        closeFolder();
        root.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        View fv = views.get(f.id);
        folder = new FolderPopup(this, f, fv != null && fv.isAttachedToWindow() ? U.rectIn(fv, root) : null);
        root.addView(folder, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        updateSystemBars();
    }

    void closeFolder() {
        if (folder == null) return;
        folder.saveTitle();
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(folder.getWindowToken(), 0);
        root.removeView(folder);
        folder = null;
        updateSystemBars();
    }

    // ====================================================================== sheets and menus

    void showSheet(View v) {
        closeSheet();
        sheet = v;
        root.addView(v, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        updateSystemBars();
    }

    void closeSheet() {
        if (sheet == null) return;
        root.removeView(sheet);
        sheet = null;
        updateSystemBars();
    }

    void showQuickMenu() {
        closeQuickMenu();
        quick = new QuickMenu(this);
        root.addView(quick, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        updateSystemBars();
    }

    void closeQuickMenu() {
        if (quick == null) return;
        root.removeView(quick);
        quick = null;
        updateSystemBars();
    }

    void onEmptyLongPress() {
        if (root.state != DragLayer.IDLE) return;
        if (prefs.locked()) { U.toast(this, "Home layout is locked. Unlock it in Horizon settings."); return; }
        root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(pal.scrim);
        scrim.setClickable(true);
        scrim.setOnClickListener(v -> closeSheet());
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        Sq bg = U.round(pal.solid, pal.radius);
        if (pal.rim != 0) bg.setStroke(U.dp(1), pal.rim);
        card.setBackground(bg);
        card.setPadding(U.dp(16), U.dp(16), U.dp(16), U.dp(16));
        card.setClickable(true);
        TextView h = U.text(this, 20, pal.fg, U.MEDIUM);
        h.setText("Customize Home");
        h.setPadding(U.dp(6), 0, 0, U.dp(14));
        card.addView(h);
        LinearLayout r1 = new LinearLayout(this), r2 = new LinearLayout(this);
        r1.addView(tile(Glyph.WIDGETS, "Widget on Home", () -> openWidgets(null, Item.DESKTOP)));
        r1.addView(tile(Glyph.MUSIC, "Now Playing", this::addMediaTile));
        r1.addView(tile(Glyph.PALETTE, "Look", this::showQuickMenu));
        r2.addView(tile(Glyph.WALLPAPER, "Wallpaper", () -> startSafely(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "Set wallpaper"))));
        r2.addView(tile(Glyph.SUN, "Custom widgets", this::showCustomWidgets));
        r2.addView(tile(Glyph.SETTINGS, "Settings", () -> startSafely(new Intent(this, SettingsActivity.class))));
        card.addView(r1);
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        r2lp.topMargin = U.dp(10);
        card.addView(r2, r2lp);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        lp.setMargins(U.dp(12), 0, U.dp(12), insetBottom + U.dp(12));
        scrim.addView(card, lp);
        showSheet(scrim);
    }

    private View tile(int glyph, String label, Runnable r) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(U.dp(4), U.dp(14), U.dp(4), U.dp(14));
        t.setBackground(U.round(pal.sunk, Math.min(20, pal.radius)));
        t.setClickable(true);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new Glyph(glyph, pal.accent));
        t.addView(iv, new LinearLayout.LayoutParams(U.dp(26), U.dp(26)));
        TextView tv = U.text(this, 13, pal.fg, U.MEDIUM);
        tv.setText(label);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, U.dp(8), 0, 0);
        t.addView(tv);
        t.setOnClickListener(v -> { if (sheet != null) closeSheet(); r.run(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(U.dp(5), 0, U.dp(5), 0);
        t.setLayoutParams(lp);
        return t;
    }

    private void addMediaTile() {
        Item m = new Item();
        m.type = Item.MEDIA;
        m.spanX = Math.min(4, cols);
        m.spanY = 2;
        place(m, true);
    }

    // ====================================================================== weather on the clock header

    /** Current conditions from Open-Meteo at your approximate location, refreshed at most every 30 minutes. */
    void refreshWeather(boolean force) {
        if (glance == null) return;
        if (checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            glance.setWeather(null, 0, true);
            return;
        }
        if (wxText != null) { glance.setWeather(wxText, wxGlyph, false); glance.setWeatherDetail(wxDetail); }
        String knownTown = prefs.sp.getString("wxPlace", "");
        if (!knownTown.isEmpty()) glance.setPlace(knownTown); // remembered across restarts
        if (!force && System.currentTimeMillis() - wxAt < 30 * 60_000L) return;
        wxAt = System.currentTimeMillis();
        if (cloud == null) cloud = new Cloud(this);
        String unit = "F".equals(prefs.units()) ? "fahrenheit" : "celsius";
        cloud.locate(fix -> {
            if (fix == null) return;
            String u = String.format(java.util.Locale.ROOT,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto&temperature_unit=%s",
                    fix[0], fix[1], unit);
            cloud.fetchText(u, (ok, body) -> runOnUiThread(() -> {
                if (!ok) return;
                try {
                    org.json.JSONObject c = new org.json.JSONObject(body).getJSONObject("current");
                    int code = c.optInt("weather_code"), t = (int) Math.round(c.optDouble("temperature_2m"));
                    boolean day = c.optInt("is_day", 1) == 1;
                    wxGlyph = code == 0 ? (day ? Glyph.SUN : Glyph.MOON)
                            : code <= 2 ? (day ? WeatherIcon.PARTLY_DAY : WeatherIcon.PARTLY_NIGHT)
                            : code == 45 || code == 48 ? WeatherIcon.FOG
                            : code >= 95 ? Glyph.FLASH
                            : (code >= 71 && code <= 77) || code == 85 || code == 86 ? WeatherIcon.SNOW
                            : (code >= 51 && code <= 67) || (code >= 80 && code <= 82) ? Glyph.RAIN : Glyph.CLOUD;
                    wxText = t + "°";
                    org.json.JSONObject d = new org.json.JSONObject(body).optJSONObject("daily");
                    String hl = "";
                    if (d != null && d.optJSONArray("temperature_2m_max") != null)
                        hl = "  H:" + Math.round(d.getJSONArray("temperature_2m_max").getDouble(0)) + "°  L:" + Math.round(d.getJSONArray("temperature_2m_min").getDouble(0)) + "°";
                    wxDetail = condition(code, day) + hl;
                    if (glance != null) { glance.setWeather(wxText, wxGlyph, false); glance.setWeatherDetail(wxDetail); }
                } catch (Exception ignored) { }
            }));
        }, place -> {
            if (place == null) return;
            prefs.sp.edit().putString("wxPlace", String.valueOf(place)).apply();
            if (glance != null) runOnUiThread(() -> glance.setPlace(String.valueOf(place)));
        });
    }

    private String wxDetail;

    /** WMO weather code in plain words. */
    private static String condition(int c, boolean day) {
        if (c == 0) return day ? "Sunny" : "Clear";
        if (c <= 2) return "Partly cloudy";
        if (c == 3) return "Cloudy";
        if (c == 45 || c == 48) return "Fog";
        if (c >= 51 && c <= 57) return "Drizzle";
        if (c >= 61 && c <= 67) return "Rain";
        if (c >= 71 && c <= 77) return "Snow";
        if (c >= 80 && c <= 82) return "Showers";
        if (c >= 85 && c <= 86) return "Snow showers";
        if (c >= 95) return "Thunderstorms";
        return "Cloudy";
    }

    void onWeatherTap() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
        } else openWeatherApp();
    }

    /** Samsung Weather first, then any other weather app installed, then a weather search. */
    void openWeatherApp() {
        Intent[] tries = {
                new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("samsungweather://")).setPackage("com.sec.android.daemonapp"),
                getPackageManager().getLaunchIntentForPackage("net.oneplus.weather"),
                getPackageManager().getLaunchIntentForPackage("wangdaye.com.geometricweather"),
                getPackageManager().getLaunchIntentForPackage("com.google.android.apps.weather"),
        };
        for (Intent i : tries) if (tryStart(i)) return;
        startSafely(new Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, "weather"));
    }

    /** The calendar in Outlook (where the meetings live), else whatever calendar app is set. */
    void openOutlookCalendar() {
        android.net.Uri today = android.provider.CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(String.valueOf(System.currentTimeMillis())).build();
        if (tryStart(new Intent(Intent.ACTION_VIEW).setDataAndType(today, "time/epoch").setPackage("com.microsoft.office.outlook"))) return;
        if (tryStart(getPackageManager().getLaunchIntentForPackage("com.microsoft.office.outlook"))) return;
        startSafely(new Intent(Intent.ACTION_VIEW, today));
    }

    private boolean tryStart(Intent i) {
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); return true; } catch (Exception e) { return false; }
    }

    void onDoubleTap() {
        switch (prefs.doubleTapAction()) {
            case "lock":
                if (!LockService.lock()) {
                    U.toast(this, "Turn on Horizon in Accessibility to lock with a double-tap");
                    startSafely(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                }
                break;
            case "menu": showQuickMenu(); break;
            case "drawer": allApps.open(false); break;
            default: break;
        }
    }

    void expandShade() {
        try {
            Object sbm = getSystemService("statusbar");
            sbm.getClass().getMethod("expandNotificationsPanel").invoke(sbm);
        } catch (Exception ignored) { }
    }

    // ====================================================================== Widgets page

    // ====================================================================== widgets

    boolean hasWidgets(Apps.App a) {
        try {
            for (AppWidgetProviderInfo w : awm.getInstalledProvidersForProfile(a.user))
                if (w.provider.getPackageName().equals(a.pkg)) return true;
        } catch (Exception ignored) { }
        return false;
    }

    /**
     * Horizon's own widgets. Each entry: {glyph, name, what it adds}. New custom widgets are added to this list.
     */
    private Object[][] customWidgets() {
        return new Object[][]{
                {Glyph.SUN, "Horizon Now", (Runnable) this::addClockCard},
                {Glyph.MUSIC, "Now Playing", (Runnable) this::addMediaTile},
        };
    }

    void showCustomWidgets() {
        closeSheet();
        Rect r = new Rect(0, root.getHeight() / 2, root.getWidth(), root.getHeight() / 2);
        root.showPopup(new Popup(this, customWidgets()), r);
    }

    /** Puts the clock & weather card back at the top of Home (or says where it already is). */
    private void addClockCard() {
        for (Item i : store.items)
            if (i.type == Item.GLANCE) { workspace.snapTo(homeIndex()); U.toast(this, "Horizon Now is already on Home"); return; }
        prefs.sp.edit().putBoolean("glance", true).apply();
        ensureGlance();
        anchorHome();
        workspace.snapTo(homeIndex());
    }

    void openWidgets(String pkg, long target) {
        closeQuickMenu();
        showSheet(new WidgetPicker(this, pkg, target));
    }

    int[] spanFor(AppWidgetProviderInfo info) { return spanFor(info, home); }

    /** A widget's natural size in cells of the given grid, from the size the app asks for. */
    int[] spanFor(AppWidgetProviderInfo info, CellLayout g) {
        int width = (root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels) - U.dp(28);
        int cw = g.cellW > 1 ? g.cellW : Math.max(1, width / g.cols);
        int ch = g.cellH > 1 ? g.cellH : Math.round(cw * g.aspect);
        int sx, sy;
        if (info.minWidth > 0 && info.minHeight > 0) {
            sx = (int) Math.ceil((info.minWidth + U.dp(8)) / (double) cw);
            sy = (int) Math.ceil((info.minHeight + U.dp(8)) / (double) ch);
        } else if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0) {
            sx = info.targetCellWidth * g.cols / 4;
            sy = info.targetCellHeight;
        } else {
            sx = g.cols;
            sy = 2;
        }
        return new int[]{Math.max(1, Math.min(g.cols, sx)), Math.max(1, Math.min(6, sy))};
    }

    /** Natural size on the Widgets page for an item saved before the page became a grid. */
    int[] panelSpanFor(Item it) {
        if (it.type == Item.MEDIA) return new int[]{4, 2};
        AppWidgetProviderInfo info = it.widgetId >= 0 ? awm.getAppWidgetInfo(it.widgetId) : null;
        return info != null ? spanFor(info, panels.get(0).grid) : new int[]{4, 2};
    }

    void addWidget(AppWidgetProviderInfo info, long target) {
        closeSheet();
        int[] span = spanFor(info, target == Item.PANEL ? currentPanel().grid : home);
        Item it = new Item();
        it.type = Item.WIDGET;
        it.container = target;
        it.spanX = span[0];
        it.spanY = span[1];
        it.provider = info.provider.flattenToString();
        it.pkg = info.provider.getPackageName();
        it.user = apps.serial(info.getProfile());
        int id = host.allocateAppWidgetId();
        it.widgetId = id;
        pendingWidget = it;
        pendingInfo = info;
        boolean bound;
        try { bound = awm.bindAppWidgetIdIfAllowed(id, info.getProfile(), info.provider, null); } catch (Exception e) { bound = false; }
        if (bound) {
            configureOrFinishWidget();
        } else {
            Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.getProfile());
            try { startActivityForResult(i, REQ_BIND); } catch (Exception e) { cancelWidget(); U.toast(this, "Couldn't add that widget"); }
        }
    }

    private void configureOrFinishWidget() {
        AppWidgetProviderInfo info = pendingInfo;
        boolean optional = Build.VERSION.SDK_INT >= 28
                && (info.widgetFeatures & AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0;
        if (info.configure != null && !optional) {
            try {
                host.startAppWidgetConfigureActivityForResult(this, pendingWidget.widgetId, 0, REQ_CONFIG, null);
                return;
            } catch (Exception ignored) { }
        }
        finishWidget();
    }

    private long stackTarget;

    private void finishWidget() {
        Item it = pendingWidget;
        pendingWidget = null;
        pendingInfo = null;
        clearPendingWidget(it.pkg);
        Item st = stackTarget != 0 ? store.byId(stackTarget) : null;
        stackTarget = 0;
        if (st != null && stackable(st)) {
            store.add(it);
            stackInto(st, it);
            reloadLayouts();
            return;
        }
        if (it.container == Item.PANEL) {
            WidgetPanel wp = currentPanel();
            it.screen = wp.page + 1;
            int[] at = store.findFree(Item.PANEL, it.screen, 4, rowsFor(wp.grid, it.spanY), it.spanX, it.spanY, it);
            it.x = at != null ? at[0] : 0;
            it.y = at != null ? at[1] : rowsFor(wp.grid, 0);
            store.add(it);
            store.save();
            wp.rebuild();
            workspace.snapTo(firstPanelIndex() + wp.page);
            final int y = it.y;
            wp.post(() -> wp.smoothScrollTo(0, Math.max(0, wp.grid.getTop() + y * wp.grid.cellH - wp.getHeight() / 3)));
        } else {
            place(it, true);
        }
    }

    private void cancelWidget() {
        if (pendingWidget != null) { try { host.deleteAppWidgetId(pendingWidget.widgetId); } catch (Exception ignored) { } }
        pendingWidget = null;
        pendingInfo = null;
    }

    /** Resize handles for any tile, widget or card on Home or the Widgets page. */
    void showResize(View v) {
        Item i = (Item) v.getTag();
        if (i == null || prefs.locked()) return;
        CellLayout cl = layoutOf(i);
        if (cl == null || cl == hotseat) return;
        boolean h = true, vert = true;
        int minX = 1, minY = 1;
        if (v instanceof WidgetFrame && ((WidgetFrame) v).info != null) {
            AppWidgetProviderInfo info = ((WidgetFrame) v).info;
            if (info.resizeMode != 0) {
                h = (info.resizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0;
                vert = (info.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0;
            }
            if (info.minResizeWidth > 0 && cl.cellW > 0) minX = (int) Math.ceil(info.minResizeWidth / (double) cl.cellW);
            if (info.minResizeHeight > 0 && cl.cellH > 0) minY = (int) Math.ceil(info.minResizeHeight / (double) cl.cellH);
        }
        if (i.type == Item.GLANCE) { minX = 2; minY = 1; }
        ResizeFrame r = new ResizeFrame(this, v, i, cl, h, vert, minX, minY);
        if (!r.resizable()) { U.toast(this, "This widget can't be resized. Drag it to move it."); return; }
        root.showResize(r);
    }

    // ====================================================================== system integration

    boolean isDefaultHome() {
        Intent h = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo r = getPackageManager().resolveActivity(h, PackageManager.MATCH_DEFAULT_ONLY);
        return r != null && r.activityInfo != null && getPackageName().equals(r.activityInfo.packageName);
    }

    @Override
    public void onPageScroll(float pos, float feedP) {
        pagePos = pos;
        indicator.setPos(pos);
        updateBottom();
        if (wallpaper.active()) applyTilt(); else updateWallpaper();
    }

    /** Parallax: the wallpaper pans with the pages and drifts gently with Home scrolling. */
    private void updateWallpaper() {
        android.os.IBinder token = root.getWindowToken();
        if (token == null) return;
        WallpaperManager wm = WallpaperManager.getInstance(this);
        int pages = Math.max(1, workspace.getChildCount());
        try {
            if (prefs.parallax() && pages > 1) {
                wm.setWallpaperOffsetSteps(1f / (pages - 1), 1f);
                int max = Math.max(1, home.getHeight() - homeScroll.getHeight());
                float y = 0.5f + 0.3f * (Math.min(1f, homeScroll.getScrollY() / (float) max) - 0.5f) + tiltY * 0.06f;
                wm.setWallpaperOffsets(token, U.clamp(pagePos / (pages - 1) + tiltX * 0.03f, 0f, 1f), U.clamp(y, 0f, 1f));
            } else {
                wm.setWallpaperOffsetSteps(1f, 1f);
                wm.setWallpaperOffsets(token, 0.5f, 0.5f);
            }
        } catch (Exception ignored) { }
    }

    @Override
    public void onPageChanged(int page) {
        if (feed != null && page == 0) feed.refresh();
        if (page != homeIndex()) quietCloseInline();
        updateSystemBars();
    }

    private boolean drawerAnimating, drawerOver;

    /** Asks the variable-refresh display for its top rate while something moves (it otherwise idles as low as 20 Hz,
     *  which makes a sliding drawer judder and look doubled). Back to the system default when still. */
    void fast(boolean on) {
        if (Build.VERSION.SDK_INT < 35) return;
        if (!on && drawerAnimating) return;
        float r = on ? View.REQUESTED_FRAME_RATE_CATEGORY_HIGH : View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT;
        root.setRequestedFrameRate(r);
        allApps.setRequestedFrameRate(r);
        workspace.setRequestedFrameRate(r);
    }

    void onDrawerProgress(float p) {
        drawerProgress = p;
        // While the drawer slides, Home and the drawer are drawn from cached layers: fading a screen full of
        // widgets otherwise redraws everything off-screen every frame and the animation drops frames (ghosting).
        boolean anim = p > 0f && p < 1f;
        if (anim != drawerAnimating) {
            drawerAnimating = anim;
            int t = anim ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE;
            workspace.setLayerType(t, null);
            bottom.setLayerType(t, null);
            allApps.setLayerType(t, null);
            fast(anim);
        }
        workspace.setVisibility(p >= 1f ? View.INVISIBLE : View.VISIBLE); // fully covered: don't draw Home at all
        // redraw the whole screen while the drawer moves: partial redraws left stale copies of the drawer on the
        // display (visible to the eye, never in screenshots, because a screenshot redraws everything)
        root.invalidate();
        if (wallpaper != null) wallpaper.invalidate();
        workspace.setAlpha(1f - p);
        float s = 1f - 0.04f * p;
        workspace.setScaleX(s);
        workspace.setScaleY(s);
        updateBottom();
        boolean over = p > 0.5f;
        if (over != drawerOver || p == 0f || p == 1f) { drawerOver = over; updateSystemBars(); }
        if (p == 1f) stopTiltQuiet(); else if (p == 0f && sensors != null && tiltOff) { tiltOff = false; startTilt(); }
    }

    private void updateBottom() {
        float away = Math.min(1f, pagePos < homeIndex() ? homeIndex() - pagePos : pagePos > lastHomeIndex() ? pagePos - lastHomeIndex() : 0f);
        float a = (1f - away) * (1f - drawerProgress);
        bottom.setAlpha(a);
        bottom.setTranslationY(away * U.dp(56));
        bottom.setVisibility(a < 0.02f ? View.INVISIBLE : View.VISIBLE);
    }

    private void updateSystemBars() {
        WindowInsetsController c = getWindow().getInsetsController();
        if (c == null || root == null) return;
        boolean overPanel = drawerProgress > 0.5f || sheet != null || quick != null || folder != null;
        boolean light = overPanel ? Color.luminance(pal.solid | 0xff000000) > 0.5f : darkText;
        int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        c.setSystemBarsAppearance(light ? mask : 0, mask);
        if (prefs.statusBar() || overPanel) c.show(WindowInsets.Type.statusBars());
        else {
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            c.hide(WindowInsets.Type.statusBars());
        }
    }

    /** Dock size changed: apps that no longer fit move to Home. */
    private void fixDock() {
        int n = prefs.dockCount();
        for (Item i : new ArrayList<>(store.items)) {
            if (i.container != Item.HOTSEAT || i.x < n) continue;
            i.container = Item.DESKTOP;
            i.spanX = i.spanY = 1;
            int[] at = store.findFree(Item.DESKTOP, 0, cols, homeRowsFor(1), 1, 1, i);
            if (at != null) { i.x = at[0]; i.y = at[1]; }
        }
        store.save();
    }

    /** Clock and date header on or off. Turning it on makes room at the top. */
    private void ensureGlance() {
        Item g = null;
        for (Item i : store.items) if (i.type == Item.GLANCE) g = i;
        if (!prefs.glance()) {
            if (g != null) {
                store.items.remove(g);
                // close the gap the card left at the top
                int top = Integer.MAX_VALUE;
                for (Item i : store.items) if (i.container == Item.DESKTOP && i.screen == 0) top = Math.min(top, i.y);
                if (top != Integer.MAX_VALUE && top > 0) for (Item i : store.items) if (i.container == Item.DESKTOP && i.screen == 0) i.y -= top;
                store.save();
            }
            return;
        }
        if (g != null) return;
        int h = 2;
        for (Item i : store.items) if (i.container == Item.DESKTOP) i.y += h;
        g = new Item();
        g.type = Item.GLANCE;
        g.spanX = cols;
        g.spanY = h;
        store.add(g);
        store.save();
    }

    private void autoAdd(String pkg, UserHandle u) {
        if (!prefs.autoAdd()) return;
        long serial = apps.serial(u);
        for (Item i : store.items) if (pkg.equals(i.pkg) && i.user == serial) return;
        for (Apps.App a : apps.all) {
            if (!a.pkg.equals(pkg) || a.serial != serial) continue;
            place(Item.app(a), true);
            return;
        }
    }

    // ====================================================================== app changes

    private void appsChanged() {
        apps.load();
        apps.clearIcons();
        shortcutIcons.clear();
        tileColors.clear();
        bind();
        allApps.filter();
    }

    private void packageRemoved(String pkg, UserHandle u) {
        long serial = apps.serial(u);
        List<Item> gone = new ArrayList<>();
        for (Item i : store.items)
            if ((i.type == Item.APP || i.type == Item.SHORTCUT || i.type == Item.WIDGET) && pkg.equals(i.pkg) && i.user == serial) gone.add(i);
        List<Item> folders = new ArrayList<>();
        for (Item i : gone) {
            Item parent = store.byId(i.container);
            if (parent != null && parent.type == Item.FOLDER) folders.add(parent);
            deleteItem(i);
        }
        for (Item f : folders) {
            List<Item> kids = store.children(f.id);
            if (kids.isEmpty()) store.items.remove(f);
            else if (kids.size() == 1) {
                Item c = kids.get(0);
                c.container = f.container; c.x = f.x; c.y = f.y; c.spanX = f.spanX; c.spanY = f.spanY;
                store.items.remove(f);
            }
        }
        store.save();
        apps.load();
        bind();
        allApps.filter();
    }

    /** A dock set up before the 4-app default keeps its size (up to 5). */
    private void keepDockSize() {
        int max = -1;
        for (Item i : store.items) if (i.container == Item.HOTSEAT) max = Math.max(max, i.x);
        if (max >= prefs.dockCount() && !prefs.sp.contains("dockCount")) prefs.sp.edit().putInt("dockCount", Math.min(5, max + 1)).apply();
    }

    private boolean outOfGrid() {
        for (Item i : store.items) if (i.container == Item.DESKTOP && i.x + i.spanX > cols) return true;
        for (Item i : store.items) if (i.container == Item.HOTSEAT && i.x >= prefs.dockCount()) return true;
        return false;
    }

    /** Moves anything that ended up below the screen into the nearest free space that is visible. */
    private final Runnable settledOverflow = this::fixOverflow;

    private void fixOverflow() {
        int fit = home.fitRows;
        if (fit <= 0 || !home.isLaidOut() || insetBottom == 0) return;
        int maxRow = 0;
        for (Item i : store.items) if (i.container == Item.DESKTOP && i.screen == 0) maxRow = Math.max(maxRow, i.y + i.spanY);
        if (maxRow <= fit) return; // it fits now that the screen has settled: leave your layout alone
        boolean moved = false;
        List<Item> desk = new ArrayList<>();
        for (Item i : store.items) if (i.container == Item.DESKTOP && i.screen == 0) desk.add(i);
        desk.sort((a, b) -> a.y != b.y ? Integer.compare(a.y, b.y) : Integer.compare(a.x, b.x));
        for (Item i : desk) {
            if (i.y + i.spanY <= fit) continue;
            i.spanY = Math.min(i.spanY, fit);
            int[] at = store.nearestFree(Item.DESKTOP, 0, cols, fit, i.spanX, i.spanY, i, i.x, i.y);
            if (at == null && i.spanX > 1) { i.spanX = 1; i.spanY = 1; at = store.nearestFree(Item.DESKTOP, 0, cols, fit, 1, 1, i, i.x, i.y); }
            if (at != null) { i.x = at[0]; i.y = at[1]; moved = true; }
        }
        if (moved) { store.save(); bind(); }
    }

    /**
     * Home the Pixel / One UI way: cards and widgets stacked from the top, app icons and folders gathered at the
     * bottom right above the dock, where the thumb is. Order is kept in both groups.
     */
    void anchorHome() {
        int fit = home.fitRows;
        if (fit <= 0) { home.post(this::anchorHome); return; }
        List<Item> big = new ArrayList<>(), small = new ArrayList<>();
        for (Item i : store.items) if (i.container == Item.DESKTOP && i.screen == 0) (i.isIcon() && i.spanX == 1 && i.spanY == 1 ? small : big).add(i);
        java.util.Comparator<Item> order = (a, b) -> a.y != b.y ? Integer.compare(a.y, b.y) : Integer.compare(a.x, b.x);
        big.sort(order);
        small.sort(order);
        for (Item i : big) { i.x = -100; i.y = -100; }
        for (Item i : small) { i.x = -100; i.y = -100; }
        for (Item i : big) {
            int[] at = store.findFree(Item.DESKTOP, 0, cols, fit, Math.min(i.spanX, cols), Math.min(i.spanY, fit), i);
            if (at == null) at = new int[]{0, 0};
            i.x = at[0]; i.y = at[1];
        }
        int rows = (small.size() + cols - 1) / cols, start = Math.max(0, fit - rows);
        for (int k = 0; k < small.size(); k++) {
            Item i = small.get(k);
            int tx = k % cols, ty = start + k / cols;
            boolean[][] g = store.grid(Item.DESKTOP, 0, cols, fit, i);
            if (ty < fit && Store.fits(g, tx, ty, 1, 1)) { i.x = tx; i.y = ty; continue; }
            int[] at = store.nearestFree(Item.DESKTOP, 0, cols, fit, 1, 1, i, tx, Math.min(ty, fit - 1));
            if (at != null) { i.x = at[0]; i.y = at[1]; }
        }
        store.save();
        bind();
    }

    /** After a column change, re-packs Home in reading order so the new grid fills without gaps. */
    private void reflow(int oldCols) {
        List<Item> desk = new ArrayList<>();
        for (Item i : store.items) if (i.container == Item.DESKTOP && i.screen == 0) desk.add(i);
        desk.sort((a, b) -> a.y != b.y ? Integer.compare(a.y, b.y) : Integer.compare(a.x, b.x));
        for (Item i : desk) { i.x = -100; i.y = -100; } // out of the way while placing
        for (Item i : desk) {
            if (i.spanX >= oldCols) i.spanX = cols; // full-width cards stay full width
            i.spanX = Math.min(i.spanX, cols);
            int[] at = store.findFree(Item.DESKTOP, 0, cols, 400, i.spanX, i.spanY, i);
            i.x = at != null ? at[0] : 0;
            i.y = at != null ? at[1] : 0;
        }
        store.save();
    }

    /** After a column change, keeps tiles inside the grid and moves overlaps to the nearest gap. */
    private void fixGrid() {
        List<Item> desk = new ArrayList<>();
        for (Item i : store.items) if (i.container == Item.DESKTOP) desk.add(i);
        desk.sort((a, b) -> a.y != b.y ? Integer.compare(a.y, b.y) : Integer.compare(a.x, b.x));
        for (Item i : desk) {
            if (i.type == Item.GLANCE && i.spanX > cols) i.spanX = cols;
            i.spanX = Math.min(i.spanX, cols);
            i.x = Math.min(i.x, cols - i.spanX);
            int rows = homeRowsFor(i.spanY);
            boolean[][] g = store.grid(Item.DESKTOP, 0, cols, rows, i);
            if (Store.fits(g, i.x, i.y, i.spanX, i.spanY)) continue;
            int[] at = store.nearestFree(Item.DESKTOP, 0, cols, rows, i.spanX, i.spanY, i, i.x, i.y);
            if (at != null) { i.x = at[0]; i.y = at[1]; }
        }
        store.save();
    }

    // ====================================================================== first run

    private void defaultLayout() {
        if (prefs.glance()) {
            Item g = new Item();
            g.type = Item.GLANCE;
            g.spanX = cols;
            g.spanY = 2;
            store.add(g);
        }

        Item media = new Item();
        media.type = Item.MEDIA;
        media.spanX = Math.min(4, cols);
        media.spanY = 2;
        putNext(media);

        List<Apps.App> dock = new ArrayList<>();
        addDistinct(dock, apps.defaultFor(new Intent(Intent.ACTION_DIAL)));
        addDistinct(dock, apps.first(Telephony.Sms.getDefaultSmsPackage(this)));
        addDistinct(dock, apps.defaultFor(new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))));
        addDistinct(dock, apps.defaultFor(new Intent(MediaStore.ACTION_IMAGE_CAPTURE)));
        addDistinct(dock, apps.defaultFor(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))));
        List<String> used = new ArrayList<>();
        for (int k = 0; k < Math.min(5, dock.size()); k++) {
            Item it = Item.app(dock.get(k));
            it.container = Item.HOTSEAT;
            it.x = k;
            store.add(it);
            used.add(dock.get(k).key);
        }

        String[] favorites = {"com.google.android.calendar", "com.samsung.android.calendar", "com.microsoft.office.outlook", "com.google.android.gm",
                "com.microsoft.teams", "com.Slack", "com.microsoft.todos", "com.google.android.apps.tasks", "com.spotify.music",
                "com.whatsapp", "com.google.android.apps.docs", "com.google.android.keep", "com.google.android.apps.maps", "com.google.android.apps.photos"};
        int count = 0;
        for (String p : favorites) {
            Apps.App a = apps.first(p);
            if (a == null || used.contains(a.key) || count >= 8) continue;
            Item it = Item.app(a);
            putNext(it);
            used.add(a.key);
            count++;
        }

        Map<String, List<Apps.App>> byCat = new LinkedHashMap<>();
        for (Apps.App a : apps.all) {
            String n = categoryName(a.category);
            if (n == null || used.contains(a.key) || a.work) continue;
            byCat.computeIfAbsent(n, k -> new ArrayList<>()).add(a);
        }
        for (Map.Entry<String, List<Apps.App>> e : byCat.entrySet()) {
            if (e.getValue().size() < 2) continue;
            Item f = new Item();
            f.type = Item.FOLDER;
            f.title = e.getKey();
            putNext(f);
            int rank = 0;
            for (Apps.App a : e.getValue()) {
                Item it = Item.app(a);
                it.container = f.id;
                it.x = rank++;
                store.add(it);
            }
        }

        Item pm = new Item();
        pm.type = Item.MEDIA;
        pm.container = Item.PANEL;
        pm.spanY = 1;
        store.add(pm);
        store.save();
    }

    private void putNext(Item it) {
        int[] at = store.findFree(Item.DESKTOP, 0, cols, homeRowsFor(it.spanY) + 40, it.spanX, it.spanY, it);
        it.container = Item.DESKTOP;
        it.x = at != null ? at[0] : 0;
        it.y = at != null ? at[1] : 0;
        store.add(it);
    }

    private static void addDistinct(List<Apps.App> list, Apps.App a) {
        if (a == null) return;
        for (Apps.App b : list) if (b.key.equals(a.key)) return;
        list.add(a);
    }
}
