package com.praiselab.horizon;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Horizon settings. Every change applies to Home as soon as you go back. */
public class SettingsActivity extends Activity {
    private static final java.util.List<String> INT_KEYS = java.util.Arrays.asList("cols", "dim", "drawerCols", "dockCount");
    private Prefs prefs;
    private U.Pal pal;
    private LinearLayout col;
    private ScrollView scroll;

    @Override
    protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(Prefs.scaled(base)); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(this);
        prefs = new Prefs(this);
        getWindow().setDecorFitsSystemWindows(false);
        scroll = new ScrollView(this);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col);
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v, ins) -> {
            android.graphics.Insets s = ins.getInsets(WindowInsets.Type.systemBars());
            col.setPadding(U.dp(16), s.top + U.dp(8), U.dp(16), s.bottom + U.dp(24));
            return ins;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        pal = U.pal(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(pal.solid | 0xff000000));
        getWindow().setStatusBarColor(0);
        getWindow().setNavigationBarColor(0);
        int y = scroll.getScrollY();
        col.removeAllViews();

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = new ImageView(this);
        back.setImageDrawable(new Glyph(Glyph.HOME, pal.fg));
        back.setPadding(U.dp(10), U.dp(10), U.dp(10), U.dp(10));
        back.setBackground(U.round(pal.chip, 22));
        back.setContentDescription("Back to Home");
        back.setOnClickListener(v -> finish());
        head.addView(back, new LinearLayout.LayoutParams(U.dp(44), U.dp(44)));
        TextView t = U.text(this, 28, pal.fg, U.MEDIUM);
        t.setText("Horizon settings");
        t.setPadding(U.dp(14), 0, 0, 0);
        head.addView(t);
        head.setPadding(0, U.dp(8), 0, U.dp(12));
        col.addView(head);
        col.addView(searchBox());

        LinearLayout look = section("Look");
        choice(look, "Style", U.STYLES, U.STYLE_NAMES, "style", prefs.style());
        choice(look, "Mode", U.MODES, U.MODE_NAMES, "mode", prefs.mode());
        toggle(look, "Liquid Glass", "Spatial look only: the wallpaper, blurred, behind every card. Refreshed when things settle, so it stays fast", "liquidGlass", prefs.sp.getBoolean("liquidGlass", true));
        choice(look, "Text size", new String[]{"80", "90", "100", "110", "120"}, new String[]{"Smallest (80%)", "Small (90%)", "Default (100%)", "Large (110%)", "Largest (120%)"}, "textSize", String.valueOf(prefs.textPct()));
        choice(look, "Content size", new String[]{"85", "92", "100", "108"}, new String[]{"Compact (85%)", "Smaller (92%)", "Default (100%)", "Larger (108%)"}, "uiSize", String.valueOf(prefs.uiPct()));
        choice(look, "Icon shape", new String[]{"circle", "squircle", "square"}, new String[]{"Circle", "Squircle", "Rounded square"}, "iconShape", prefs.iconShape());
        choice(look, "Icon style", new String[]{"color", "themed", "themedAll"},
                new String[]{"Original colors", "Themed (apps that support it)", "Themed (all apps, tint the rest)"},
                "iconStyle", prefs.sp.getString("iconStyle", "color"));
        choice(look, "Icon size", new String[]{"0.85", "1.0", "1.15"}, new String[]{"Small", "Medium", "Large"}, "iconScale", String.valueOf(prefs.iconScale()));
        choice(look, "Tile opacity", new String[]{"auto", "55", "70", "85", "100"}, new String[]{"Match the look", "55% (most see-through)", "70%", "85%", "100% (solid)"}, "tileOpacity", prefs.tileOpacity());
        toggle(look, "Names on tiles", "Show app names on medium and larger tiles", "tileLabels", prefs.tileLabels());

        LinearLayout home = section("Home screen");
        choice(home, "Tile grid", new String[]{"6", "5", "4"}, new String[]{"6 columns (more, smaller tiles)", "5 columns", "4 columns (bigger tiles)"}, "cols", String.valueOf(prefs.cols()));
        toggle(home, "Live tile previews", "Show each app's latest notification on medium and larger tiles", "previews", prefs.previews());
        toggle(home, "Horizon Now", "Time, second clock, weather and your next event at the top of Home", "glance", prefs.glance());
        choice(home, "Weather units", new String[]{"C", "F"}, new String[]{"°C", "°F"}, "units", prefs.units());
        toggle(home, "Search bar", "Search bar above the dock", "searchBar", prefs.searchBar());
        choice(home, "Dock", new String[]{"5", "4"}, new String[]{"5 apps", "4 apps"}, "dockCount", String.valueOf(prefs.dockCount()));
        choice(home, "Folders open", new String[]{"inline", "popup"}, new String[]{"In place (tiles below move down)", "As a pop-up"}, "folderStyle", prefs.folderStyle());
        row(home, "Tidy up Home", "Widgets at the top, icons and folders together just above the dock, no gaps", () -> { prefs.put("tidy", true); U.toast(this, "Home tidied up"); });
        toggle(home, "Add new apps to Home", "Newly installed apps get a tile automatically", "autoAdd", prefs.autoAdd());
        toggle(home, "Lock Home layout", "Stops tiles and widgets from being moved, resized or removed by accident", "locked", prefs.locked());
        toggle(home, "Now page", "Weather, agenda, To Do and stories to the left of Home", "feed", prefs.feed());

        LinearLayout clk = section("Horizon Now");
        toggle(clk, "Animated weather", "Off: a still sky scene that uses no battery. On: the sky moves (sun, stars, clouds, rain), costing some battery", "wxAnimated", prefs.sp.getBoolean("wxAnimated", false));
        choice(clk, "Clock style", new String[]{"thin", "bold"}, new String[]{"Thin (Apple style)", "Bold"}, "clockWeight", prefs.sp.getString("clockWeight", "thin"));
        toggle(clk, "Long date", "Friday, October 9 instead of Fri, Oct 9", "dateLong", prefs.sp.getBoolean("dateLong", false));
        toggle(clk, "Upcoming meetings", "The date line shows a meeting starting within the hour", "clockEvents", prefs.sp.getBoolean("clockEvents", true));
        toggle(clk, "Second clock", "Another city's time under yours", "clockZone2", prefs.sp.getBoolean("clockZone2", true));
        choice(clk, "Second clock city", new String[]{"Asia/Kolkata", "Europe/London", "America/New_York", "America/Los_Angeles", "America/Chicago", "America/Denver", "Europe/Paris", "Europe/Berlin", "Asia/Dubai", "Asia/Singapore", "Asia/Hong_Kong", "Asia/Shanghai", "Asia/Tokyo", "Asia/Seoul", "Australia/Sydney", "UTC"},
                new String[]{"India", "London", "New York", "Los Angeles", "Chicago", "Denver", "Paris", "Berlin", "Dubai", "Singapore", "Hong Kong", "Shanghai", "Tokyo", "Seoul", "Sydney", "UTC"},
                "zone2", prefs.sp.getString("zone2", "Asia/Kolkata"));
        toggle(clk, "Town name", "Show where the weather is for", "wxTown", prefs.sp.getBoolean("wxTown", true));
        toggle(clk, "Conditions and high / low", "Sunny  H:30°  L:13°", "wxHiLo", prefs.sp.getBoolean("wxHiLo", true));
        choice(clk, "Temperature units", new String[]{"C", "F"}, new String[]{"°C", "°F"}, "units", prefs.units());

        LinearLayout np = section("Now Playing card");
        toggle(np, "Output device row", "\"This phone\" and the button to switch to headphones or speakers", "npOutput", prefs.sp.getBoolean("npOutput", true));
        toggle(np, "Shuffle and save buttons", "Shown when the music app offers them (Spotify does)", "npExtras", prefs.sp.getBoolean("npExtras", true));
        toggle(np, "Album art background", "The artwork fills the card; off gives a plain dark card", "npArt", prefs.sp.getBoolean("npArt", true));

        LinearLayout wall = section("Wallpaper");
        toggle(wall, "Parallax", "The wallpaper moves as you tilt the phone and swipe pages. Icons and widgets stay still.", "parallax", prefs.parallax());
        boolean has = WallpaperView.file(this).exists();
        row(wall, "Wallpaper for glass and parallax", has ? "Set. Tap to use a different image." : "Not set yet. Share your wallpaper with Horizon so the Spatial look's glass can blur it, and so it can move with parallax.", this::chooseParallax);
        if (has) row(wall, "Stop using the parallax image", "Show the phone's wallpaper without motion", () -> { WallpaperView.file(this).delete(); prefs.put("parallaxImage", false); render(); });
        slider(wall, "Parallax strength", "How far the wallpaper moves as you tilt and swipe", "parallaxAmt", prefs.sp.getInt("parallaxAmt", 35));
        choice(wall, "Dim wallpaper", new String[]{"0", "10", "20", "30", "40"}, new String[]{"Off", "10%", "20%", "30%", "40% (most readable)"}, "dim", String.valueOf(prefs.dim()));
        row(wall, "Use on lock screen too", "Puts the wallpaper you see on Home on the lock screen as well", this::homeToLock);
        row(wall, "Change wallpaper", "Opens your phone's wallpaper picker",
                () -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "Set wallpaper")));

        LinearLayout drawer = section("App drawer");
        choice(drawer, "Columns", new String[]{"5", "4"}, new String[]{"5", "4"}, "drawerCols", String.valueOf(prefs.drawerCols()));
        toggle(drawer, "Google suggestions", "Autocomplete from Google as you type (what you type is sent to Google)", "googleSuggest", prefs.sp.getBoolean("googleSuggest", true));
        toggle(drawer, "Search history", "Remember your web searches on this phone and suggest them. Long-press one to remove it", "searchHistOn", prefs.sp.getBoolean("searchHistOn", true));
        row(drawer, "Clear search history", "Forget every search you've made from Horizon", () -> { prefs.sp.edit().remove("searchHist").apply(); android.widget.Toast.makeText(this, "Search history cleared", android.widget.Toast.LENGTH_SHORT).show(); });
        toggle(drawer, "Open with keyboard", "Start typing to search as soon as the drawer opens", "drawerKeyboard", prefs.drawerKeyboard());
        row(drawer, "Recent apps from everywhere", Usage.systemAllowed(this) ? "On. Recent includes apps opened from anywhere." : "Off. Recent only counts apps opened from Horizon. Tap to allow Usage access.",
                () -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(android.net.Uri.parse("package:" + getPackageName()))));
        row(drawer, "Hidden apps", prefs.hidden().isEmpty() ? "None hidden" : prefs.hidden().size() + " hidden from the app list", this::hiddenApps);

        LinearLayout gest = section("Gestures");
        choice(gest, "Swipe down on Home", new String[]{"menu", "shade", "search", "none"}, new String[]{"Control menu", "Notifications", "Search", "Nothing"}, "swipeAction", prefs.swipeAction());
        choice(gest, "Double-tap empty space", new String[]{"none", "lock", "menu", "drawer"}, new String[]{"Nothing", "Lock the phone", "Control menu", "App drawer"}, "doubleTapAction", prefs.doubleTapAction());
        TextView gh = U.text(this, 12.5f, pal.fg3, U.REGULAR);
        gh.setText("Swipe up from the dock or search bar to open all apps. Swipe right for Now, left for Widgets.");
        gh.setPadding(U.dp(16), U.dp(4), U.dp(16), U.dp(12));
        gest.addView(gh);

        LinearLayout notif = section("Notifications");
        choice(notif, "App badges", new String[]{"count", "dot", "off"}, new String[]{"Count", "Dot", "Off"}, "badges", prefs.badges());
        boolean access = Badges.allowed(this);
        row(notif, "Notification access", access ? "Allowed. Badges, live tiles and Now Playing are on." : "Needed for badges, live tile previews and Now Playing. Tap to allow.",
                () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, new ComponentName(this, BadgeService.class).flattenToString())));

        LinearLayout bk = section("Backup");
        row(bk, "Back up now", "Save your Home layout and every Horizon setting to a file (Drive, Downloads, anywhere)", () -> {
            String name = "horizon-backup-" + android.text.format.DateFormat.format("yyyy-MM-dd", System.currentTimeMillis()) + ".json";
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE, name), REQ_BACKUP);
        });
        row(bk, "Restore from a file", "Bring back a layout and settings you saved", () ->
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_RESTORE));
        java.io.File[] snaps = Backup.list(this);
        row(bk, "Restore an earlier layout", snaps.length == 0 ? "Horizon saves a snapshot each day; none yet" : snaps.length + " automatic snapshots on this phone", this::chooseSnapshot);

        LinearLayout sys = section("System");
        toggle(sys, "Status bar", "Show the clock and icons at the top of Home", "statusBar", prefs.statusBar());
        row(sys, "Default home app", "Choose Horizon or go back to your previous launcher", () -> startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)));
        row(sys, "Reset Home layout", "Start over with the suggested tiles, folders and widgets", this::confirmReset);

        TextView about = U.text(this, 13, pal.fg3, U.REGULAR);
        about.setText("Horizon 2.0 · Everything stays on this phone except weather, stories and Microsoft sign-in, which you turn on yourself.");
        about.setPadding(U.dp(8), U.dp(16), U.dp(8), 0);
        about.setLineSpacing(0, 1.2f);
        about.setTag("about");
        col.addView(about);
        applyFilter();
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    // ---------- search ----------
    private android.widget.EditText search;
    private TextView noMatch;
    private static final java.util.Map<String, String> EXTRA = new java.util.HashMap<>();
    static {
        EXTRA.put("Text size", "font scale bigger smaller");
        EXTRA.put("Content size", "dpi density display zoom scale compact");
        EXTRA.put("Style", "theme look material glass spatial android tiles");
        EXTRA.put("Mode", "theme dark light black night auto");
        EXTRA.put("Parallax", "tilt motion gyro move");
        EXTRA.put("Parallax image", "wallpaper photo picture");
        EXTRA.put("Dock", "hotseat bottom apps");
        EXTRA.put("Tile grid", "columns rows grid layout apps per row");
        EXTRA.put("Now page", "google discover feed left news");
        EXTRA.put("Status bar", "notification bar clock top");
        EXTRA.put("Icon shape", "circle round squircle square");
        EXTRA.put("App badges", "dots count notification");
        EXTRA.put("Lock Home layout", "lock edit move");
        EXTRA.put("Hidden apps", "hide");
        EXTRA.put("Default home app", "launcher");
        EXTRA.put("Horizon Now", "clock time weather at a glance second clock calendar event");
    }

    private View searchBox() {
        if (search == null) {
            search = new android.widget.EditText(this);
            search.setSingleLine(true);
            search.setHint("Search settings");
            search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
            search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
            search.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void afterTextChanged(android.text.Editable s) { applyFilter(); }
            });
        }
        if (search.getParent() != null) ((android.view.ViewGroup) search.getParent()).removeView(search);
        search.setTextColor(pal.fg);
        search.setHintTextColor(pal.fg3);
        search.setTextSize(17);
        Glyph g = new Glyph(Glyph.SEARCH, pal.fg2);
        g.setBounds(0, 0, U.dp(20), U.dp(20));
        search.setCompoundDrawables(g, null, null, null);
        search.setCompoundDrawablePadding(U.dp(10));
        search.setPadding(U.dp(16), 0, U.dp(16), 0);
        search.setBackground(U.round(U.over(pal.solid | 0xff000000, pal.chip), 24));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, U.dp(50));
        lp.bottomMargin = U.dp(4);
        search.setLayoutParams(lp);
        return search;
    }

    /** Hides rows that don't match the search, and sections left with nothing to show. */
    private void applyFilter() {
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        boolean filtering = !q.isEmpty();
        boolean any = false;
        TextView header = null;
        for (int i = 0; i < col.getChildCount(); i++) {
            View v = col.getChildAt(i);
            Object tag = v.getTag();
            if ("section".equals(tag)) { header = (TextView) v; continue; }
            if ("card".equals(tag)) {
                String sec = header != null ? header.getText().toString().toLowerCase(java.util.Locale.ROOT) : "";
                int shown = 0;
                LinearLayout card = (LinearLayout) v;
                for (int k = 0; k < card.getChildCount(); k++) {
                    View r = card.getChildAt(k);
                    Object t = r.getTag();
                    boolean match = !filtering || (t instanceof String && matches((String) t + " " + sec, q));
                    r.setVisibility(match ? View.VISIBLE : View.GONE);
                    if (match) shown++;
                }
                v.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
                if (header != null) header.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
                if (shown > 0 && filtering) any = true;
                header = null;
            } else if ("about".equals(tag)) {
                v.setVisibility(filtering ? View.GONE : View.VISIBLE);
            }
        }
        if (noMatch == null) {
            noMatch = U.text(this, 15, pal.fg2, U.REGULAR);
            noMatch.setPadding(U.dp(8), U.dp(24), U.dp(8), 0);
        }
        if (noMatch.getParent() == null) col.addView(noMatch);
        noMatch.setTextColor(pal.fg2);
        noMatch.setText("No settings match \u201c" + q + "\u201d");
        noMatch.setVisibility(filtering && !any ? View.VISIBLE : View.GONE);
    }

    private static boolean matches(String text, String q) {
        for (String w : q.split("\\s+")) if (!text.contains(w)) return false;
        return true;
    }

    private LinearLayout section(String name) {
        TextView h = U.text(this, 14.5f, pal.accent, U.MEDIUM);
        h.setText(name.toUpperCase(java.util.Locale.ROOT));
        h.setLetterSpacing(0.08f);
        h.setPadding(U.dp(8), U.dp(18), 0, U.dp(8));
        h.setTag("section");
        col.addView(h);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        Sq bg = U.round(pal.surface | 0xff000000, Math.min(24, pal.radius));
        int page = pal.solid | 0xff000000;
        bg.setColor(U.over(page, pal.dark ? U.alpha(0xffffffff, 0.07f) : U.alpha(0xff000000, 0.04f))); // a card just off the page colour
        if (pal.style.equals("spatial")) bg.setColor(U.alpha(0xffffffff, 0.08f)); // glass card
        card.setBackground(bg);
        card.setPadding(0, U.dp(4), 0, U.dp(4));
        card.setTag("card");
        col.addView(card);
        return card;
    }

    private LinearLayout rowBase(LinearLayout card, String title, String sub) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(U.dp(16), U.dp(12), U.dp(12), U.dp(12));
        r.setMinimumHeight(U.dp(60));
        LinearLayout tc = new LinearLayout(this);
        tc.setOrientation(LinearLayout.VERTICAL);
        TextView t = U.text(this, 18, pal.fg, U.MEDIUM);
        t.setText(title);
        tc.addView(t);
        if (sub != null) {
            TextView s = U.text(this, 15, pal.fg2, U.REGULAR);
            s.setText(sub);
            s.setPadding(0, U.dp(4), 0, 0);
            s.setLineSpacing(0, 1.15f);
            tc.addView(s);
        }
        r.addView(tc, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        String extra = EXTRA.get(title);
        r.setTag((title + " " + (sub == null ? "" : sub) + " " + (extra == null ? "" : extra)).toLowerCase(java.util.Locale.ROOT));
        card.addView(r);
        return r;
    }

    private void row(LinearLayout card, String title, String sub, Runnable r) {
        View v = rowBase(card, title, sub);
        v.setClickable(true);
        v.setOnClickListener(x -> r.run());
    }

    private void toggle(LinearLayout card, String title, String sub, String key, boolean on) {
        LinearLayout r = rowBase(card, title, sub);
        Switch s = new Switch(this);
        s.setChecked(on);
        s.setThumbTintList(ColorStateList.valueOf(on ? pal.accent : pal.fg3));
        s.setTrackTintList(ColorStateList.valueOf(on ? U.alpha(pal.accent, 0.4f) : U.alpha(pal.fg3, 0.35f)));
        s.setContentDescription(title);
        s.setOnCheckedChangeListener((b, v) -> {
            prefs.put(key, v);
            render();
        });
        r.addView(s);
        r.setOnClickListener(x -> s.toggle());
    }

    private void choice(LinearLayout card, String title, String[] keys, String[] names, String pref, String current) {
        int idx = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(current)) idx = i;
        final int sel = idx;
        LinearLayout r = rowBase(card, title, names[idx]);
        r.setClickable(true);
        r.setOnClickListener(x -> new AlertDialog.Builder(this, pal.dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle(title)
                .setSingleChoiceItems(names, sel, (d, which) -> {
                    String k = keys[which];
                    if (INT_KEYS.contains(pref)) prefs.put(pref, Integer.parseInt(k));
                    else if (pref.equals("iconScale")) prefs.put(pref, Float.parseFloat(k));
                    else prefs.put(pref, k);
                    if (pref.equals("textSize") || pref.equals("uiSize")) { d.dismiss(); recreate(); return; }
                    if (pref.equals("doubleTapAction") && k.equals("lock")) {
                        U.toast(this, "Turn on Horizon under Accessibility so it can lock the screen");
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    }
                    d.dismiss();
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show());
    }

    private static final int REQ_PICK = 21, REQ_BACKUP = 22, REQ_RESTORE = 23;

    /** Opened from a card's "Edit widget": jump to that card's settings. */
    private void scrollToSection(String name) {
        if (name == null) return;
        scroll.post(() -> {
            for (int k = 0; k < col.getChildCount(); k++) {
                View v = col.getChildAt(k);
                if ("section".equals(v.getTag()) && ((TextView) v).getText().toString().equalsIgnoreCase(name)) {
                    scroll.smoothScrollTo(0, Math.max(0, v.getTop() - U.dp(8)));
                    return;
                }
            }
        });
    }

    private void chooseSnapshot() {
        java.io.File[] f = Backup.list(this);
        if (f.length == 0) { U.toast(this, "No snapshots yet"); return; }
        new AlertDialog.Builder(this, pal.dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Restore an earlier layout")
                .setItems(Backup.labels(f).toArray(new String[0]), (d, w) -> {
                    try { Backup.restoreFile(this, f[w]); U.toast(this, "Restored. Your current layout was saved first."); finish(); }
                    catch (Exception e) { U.toast(this, "Couldn't restore that snapshot"); }
                })
                .show();
    }

    /** Opened from Home's prompt: jump straight to choosing the wallpaper image. */
    @Override
    protected void onPostCreate(Bundle b) {
        super.onPostCreate(b);
        if (getIntent().getBooleanExtra("chooseWallpaper", false) && b == null) chooseParallax();
        if (b == null) scrollToSection(getIntent().getStringExtra("section"));
    }

    /** Copies the Home wallpaper (Horizon's parallax image if set, else the phone's) to the lock screen. */
    private void homeToLock() {
        android.widget.Toast.makeText(this, "Setting lock screen wallpaper\u2026", android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean ok = false;
            try {
                android.app.WallpaperManager wm = android.app.WallpaperManager.getInstance(this);
                java.io.File f = WallpaperView.file(this);
                if (f.exists()) {
                    try (java.io.InputStream in = new java.io.FileInputStream(f)) { wm.setStream(in, null, true, android.app.WallpaperManager.FLAG_LOCK); }
                    ok = true;
                } else {
                    android.graphics.drawable.Drawable dr = wm.getDrawable();
                    if (dr instanceof android.graphics.drawable.BitmapDrawable) {
                        wm.setBitmap(((android.graphics.drawable.BitmapDrawable) dr).getBitmap(), null, true, android.app.WallpaperManager.FLAG_LOCK);
                        ok = true;
                    }
                }
            } catch (Exception e) { android.util.Log.w("Horizon", "lock wallpaper", e); }
            final boolean done = ok;
            runOnUiThread(() -> android.widget.Toast.makeText(this, done ? "Lock screen wallpaper set" : "Couldn't read the Home wallpaper. Set it with \"Wallpaper for glass and parallax\" first.", android.widget.Toast.LENGTH_LONG).show());
        }).start();
    }

    private void chooseParallax() {
        new AlertDialog.Builder(this, pal.dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Parallax image")
                .setItems(new String[]{"Use my current wallpaper", "Pick a photo"}, (d, w) -> {
                    if (w == 0) useCurrentWallpaper(); else pickPhoto();
                })
                .show();
    }

    /** Needs All files access on Android 13+; Horizon only reads the wallpaper with it. */
    private void useCurrentWallpaper() {
        try {
            android.graphics.drawable.Drawable d = android.app.WallpaperManager.getInstance(this).getDrawable();
            if (d == null) throw new SecurityException();
            int w = Math.max(1, d.getIntrinsicWidth()), h = Math.max(1, d.getIntrinsicHeight());
            save(U.bitmap(d, w, h));
        } catch (Throwable e) {
            new AlertDialog.Builder(this, pal.dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                    .setTitle("Allow Horizon to read your wallpaper")
                    .setMessage("Android only shares the current wallpaper with apps that have All files access. Horizon uses it only to read the wallpaper image. Turn it on for Horizon, come back, and tap Use my current wallpaper again. Or pick the photo from your gallery instead, which needs no permission.")
                    .setPositiveButton("Open setting", (dd, ww) -> startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, android.net.Uri.parse("package:" + getPackageName()))))
                    .setNegativeButton("Pick a photo", (dd, ww) -> pickPhoto())
                    .show();
        }
    }

    private void pickPhoto() {
        Intent i = new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES).setType("image/*");
        try { startActivityForResult(i, REQ_PICK); }
        catch (Exception e) { startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), REQ_PICK); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        if (req == REQ_BACKUP) {
            try { Backup.write(this, data.getData()); U.toast(this, "Backup saved"); } catch (Exception e) { U.toast(this, "Couldn't save the backup"); }
            return;
        }
        if (req == REQ_RESTORE) {
            try { Backup.read(this, data.getData()); U.toast(this, "Restored. Your previous layout was saved as a snapshot."); finish(); }
            catch (Exception e) { U.toast(this, "That file isn't a Horizon backup"); }
            return;
        }
        if (req != REQ_PICK) return;
        try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
            save(android.graphics.BitmapFactory.decodeStream(in));
        } catch (Exception e) {
            U.toast(this, "Couldn't open that image");
        }
    }

    /** Stores a screen-sized-plus copy so the launcher can draw and move it smoothly. */
    private void save(android.graphics.Bitmap b) {
        if (b == null) { U.toast(this, "Couldn't read that image"); return; }
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float s = Math.min(1f, Math.max(dm.widthPixels * 1.3f / b.getWidth(), dm.heightPixels * 1.3f / b.getHeight()));
        android.graphics.Bitmap out = s < 1f ? android.graphics.Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * s), Math.round(b.getHeight() * s), true) : b;
        try (java.io.FileOutputStream fo = new java.io.FileOutputStream(WallpaperView.file(this))) {
            out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, fo);
            prefs.put("parallaxImage", true);
            prefs.put("parallax", true);
            U.toast(this, "Parallax image set");
            render();
        } catch (Exception e) {
            U.toast(this, "Couldn't save the image");
        }
    }

    private void slider(LinearLayout card, String title, String sub, String key, int value) {
        LinearLayout r = rowBase(card, title, sub + " · " + value + "%");
        r.setOrientation(LinearLayout.VERTICAL);
        r.setGravity(Gravity.START);
        r.getChildAt(0).setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        android.widget.SeekBar sb = new android.widget.SeekBar(this);
        sb.setMax(100);
        sb.setProgress(value);
        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(pal.accent));
        sb.setThumbTintList(android.content.res.ColorStateList.valueOf(pal.accent));
        TextView subView = (TextView) ((LinearLayout) r.getChildAt(0)).getChildAt(1);
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar s, int v, boolean user) { subView.setText(sub + " · " + v + "%"); }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) { prefs.put(key, s.getProgress()); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, U.dp(40));
        lp.topMargin = U.dp(6);
        r.addView(sb, lp);
    }

    private void hiddenApps() {
        Apps apps = new Apps(this);
        apps.load();
        List<Apps.App> all = apps.all;
        String[] names = new String[all.size()];
        boolean[] checked = new boolean[all.size()];
        Set<String> hidden = prefs.hidden();
        for (int i = 0; i < all.size(); i++) { names[i] = all.get(i).label; checked[i] = hidden.contains(all.get(i).key); }
        new AlertDialog.Builder(this, pal.dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Hide from the app list")
                .setMultiChoiceItems(names, checked, (d, w, on) -> checked[w] = on)
                .setPositiveButton("Save", (d, w) -> {
                    Set<String> out = new HashSet<>();
                    for (int i = 0; i < all.size(); i++) if (checked[i]) out.add(all.get(i).key);
                    prefs.setHidden(out);
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this, pal.dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Reset Home layout?")
                .setMessage("Your tiles, folders and widgets are replaced with the suggested layout. Apps are not uninstalled.")
                .setPositiveButton("Reset", (d, w) -> {
                    if (Launcher.current != null) Launcher.current.resetLayout();
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
