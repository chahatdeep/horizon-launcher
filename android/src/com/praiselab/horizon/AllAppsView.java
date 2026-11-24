package com.praiselab.horizon;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SectionIndexer;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Swipe-up app drawer: search on top, every app A–Z with a letter scroller. */
final class AllAppsView extends FrameLayout {
    private final Launcher l;
    private final LinearLayout col, box;
    private final EditText search;
    private final ImageView clear;
    private final TextView web, empty;
    final GridView grid;
    private final Adapter adapter = new Adapter();
    private List<Apps.App> shown = new ArrayList<>();
    private float progress;
    private ValueAnimator anim;
    private final int slop;
    private float dy0, dx0;
    private boolean closing;
    private VelocityTracker vt;

    AllAppsView(Launcher l) {
        super(l);
        this.l = l;
        slop = ViewConfiguration.get(l).getScaledTouchSlop();
        col = new LinearLayout(l);
        col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        box = new LinearLayout(l);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(U.dp(18), 0, U.dp(8), 0);
        ImageView mag = new ImageView(l);
        box.addView(mag, new LinearLayout.LayoutParams(U.dp(22), U.dp(22)));
        search = new EditText(l);
        search.setBackground(null);
        search.setSingleLine(true);
        search.setHint("Search apps, settings, web and AI");
        search.setTextSize(16);
        search.setTypeface(U.REGULAR);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(U.dp(12), 0, U.dp(8), 0);
        box.addView(search, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        clear = new ImageView(l);
        clear.setPadding(U.dp(9), U.dp(9), U.dp(9), U.dp(9));
        clear.setContentDescription("Clear search");
        clear.setVisibility(GONE);
        box.addView(clear, new LinearLayout.LayoutParams(U.dp(40), U.dp(40)));
        // Galaxy AI style: ask AI about what you typed (or open the assistant when empty)
        ImageView ai = new ImageView(l);
        ai.setImageDrawable(l.claudeIcon());
        ai.setPadding(U.dp(9), U.dp(9), U.dp(9), U.dp(9));
        ai.setContentDescription("Ask Claude");
        ai.setOnClickListener(v -> l.askClaude(query()));
        box.addView(ai, new LinearLayout.LayoutParams(U.dp(44), U.dp(44)));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, U.dp(56));
        blp.setMargins(U.dp(16), U.dp(8), U.dp(16), U.dp(8));
        col.addView(box, blp);

        chips = new LinearLayout(l);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chips.setPadding(U.dp(12), 0, U.dp(16), 0);
        chipScroll = new android.widget.HorizontalScrollView(l);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        chipScroll.addView(chips);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, U.dp(44));
        clp.bottomMargin = U.dp(4);
        col.addView(chipScroll, clp);
        library = new LibraryView(l, new LibraryView.Host() {
            @Override public void openCategory(String name) { list = true; category = name; filter(); grid.setSelection(0); }
            @Override public String categoryOf(Apps.App a) { return catOf(a); }
        });
        col.addView(library, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        library.setOnScrollChangeListener((v, x, y, ox, oy) -> { if (Math.abs(y - oy) > 0) hideKeyboard(); });

        web = U.text(l, 15, 0, U.MEDIUM);
        web.setGravity(Gravity.CENTER_VERTICAL);
        web.setPadding(U.dp(18), 0, U.dp(18), 0);
        web.setCompoundDrawablePadding(U.dp(12));
        web.setVisibility(GONE);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, U.dp(52));
        wlp.setMargins(U.dp(16), 0, U.dp(16), U.dp(4));
        col.addView(web, wlp);
        suggest = new LinearLayout(l);
        suggest.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        slp.setMargins(U.dp(16), 0, U.dp(16), U.dp(4));
        col.addView(suggest, slp);
        results = new LinearLayout(l);
        results.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rlp.setMargins(U.dp(16), 0, U.dp(16), U.dp(4));
        col.addView(results, rlp);

        empty = U.text(l, 15, 0, U.REGULAR);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(U.dp(24), U.dp(24), U.dp(24), U.dp(8));
        empty.setVisibility(GONE);
        col.addView(empty, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        grid = new GridView(l);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(0);
        grid.setClipToPadding(false);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        grid.setAdapter(adapter);
        grid.setFastScrollEnabled(true);
        grid.setVerticalScrollBarEnabled(false);
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView v, int s) { if (s == SCROLL_STATE_TOUCH_SCROLL) hideKeyboard(); }
            @Override public void onScroll(AbsListView v, int a, int b, int c) { }
        });
        col.addView(grid, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        // searching: matching apps first, then everything else in one scrolling list (never under the keyboard)
        col.removeView(grid);
        col.addView(grid, col.indexOfChild(web), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout found = new LinearLayout(l);
        found.setOrientation(LinearLayout.VERTICAL);
        for (View v : new View[]{web, suggest, results, empty}) {
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            col.removeView(v);
            found.addView(v, lp);
        }
        foundScroll = new android.widget.ScrollView(l);
        foundScroll.setVerticalScrollBarEnabled(false);
        foundScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        foundScroll.addView(found, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        foundScroll.setOnScrollChangeListener((v, x, y, ox, oy) -> { if (Math.abs(y - oy) > U.dp(4)) hideKeyboard(); });
        foundScroll.setVisibility(GONE);
        col.addView(foundScroll, col.indexOfChild(grid) + 1, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        search.setOnFocusChangeListener((v, has) -> showSuggestions(query()));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { removeCallbacks(refilter); postDelayed(refilter, 90); }
        });
        search.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                if (!shown.isEmpty() && query().length() > 0) l.launchApp(shown.get(0), grid.getChildCount() > 0 ? grid.getChildAt(0) : this);
                else if (query().length() > 0) l.webSearch(query());
                else return true;
                // a fresh box next time (after the app or results have opened, so nothing jumps on screen)
                hideKeyboard();
                postDelayed(() -> search.setText(""), 600);
                return true;
            }
            return false;
        });
        clear.setOnClickListener(v -> search.setText(""));
        web.setOnClickListener(v -> { l.webSearch(query()); postDelayed(() -> search.setText(""), 600); });
        // the search bar lives at the bottom, within thumb reach, and rides up with the keyboard
        col.removeView(box);
        // one rounded tray at the bottom holds Recent and the search bar, like the Home dock
        tray = new LinearLayout(l);
        tray.setOrientation(LinearLayout.VERTICAL);
        tray.setPadding(U.dp(8), U.dp(4), U.dp(8), U.dp(8));
        tray.addView(library.recentStrip, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams bb = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, U.dp(52));
        bb.topMargin = U.dp(4);
        tray.addView(box, bb);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tl.setMargins(U.dp(10), 0, U.dp(10), U.dp(8));
        col.addView(tray, tl);
        // cards fade out as they scroll under the tray instead of being cut off
        library.setVerticalFadingEdgeEnabled(true);
        library.setFadingEdgeLength(U.dp(36));
        setVisibility(GONE);
        restyle();
    }

    private int navBottom, imeBottom;

    /** Keyboard height (0 when hidden), so the bottom search bar sits right above it. */
    void setIme(int ime) {
        if (ime == imeBottom) return;
        imeBottom = ime;
        col.setPadding(col.getPaddingLeft(), col.getPaddingTop(), col.getPaddingRight(), Math.max(navBottom, imeBottom));
    }

    void restyle() {
        U.Pal p = l.pal;
        setBackgroundColor(p.solid | 0xff000000); // solid, so Home never shows through as a second layer
        int trayColor = U.over(p.solid | 0xff000000, p.dark ? 0x14ffffff : 0x0d000000);
        if (tray != null) tray.setBackground(U.round(trayColor, U.R_CARD + 8));
        // a clearly lighter (or darker) field than the tray, so the search bar reads at a glance
        box.setBackground(U.round(U.over(trayColor, p.dark ? 0x29ffffff : 0x14000000), U.R_CARD));
        ((ImageView) box.getChildAt(0)).setImageDrawable(new Glyph(Glyph.SEARCH, p.fg));
        search.setTextColor(p.fg);
        search.setHintTextColor(p.fg2);
        clear.setImageDrawable(new Glyph(Glyph.CLOSE, p.fg2));
        clear.setBackground(U.round(p.solid, 20));
        web.setTextColor(p.fg);
        web.setBackground(U.round(p.sunk, 18));
        Glyph g = new Glyph(Glyph.SEARCH, p.accent);
        g.setBounds(0, 0, U.dp(20), U.dp(20));
        web.setCompoundDrawables(g, null, null, null);
        empty.setTextColor(p.fg2);
        grid.setNumColumns(l.prefs.drawerCols());
        library.invalidateContent();
        filter();
    }

    void setInsets(int top, int bottom) {
        navBottom = bottom;
        col.setPadding(0, top + U.dp(8), 0, Math.max(bottom, imeBottom));
        grid.setPadding(U.dp(8), U.dp(4), U.dp(8), U.dp(12));
        library.setBottomInset(0);
    }

    private String query() { return search.getText().toString().trim(); }

    // ---------- universal search: AI, settings, contacts, phone, web ----------
    private final LinearLayout results, suggest;
    private android.widget.ScrollView foundScroll;

    /** Searching: the app grid shrinks to its matches (up to two rows) and the rest scrolls below. */
    private void layoutForSearch(boolean searching) {
        if (foundScroll == null) return;
        foundScroll.setVisibility(searching ? VISIBLE : GONE);
        LinearLayout.LayoutParams gl = (LinearLayout.LayoutParams) grid.getLayoutParams();
        if (searching) {
            int cols = Math.max(1, grid.getNumColumns() > 0 ? grid.getNumColumns() : l.prefs.drawerCols());
            int rows = Math.min(2, (shown.size() + cols - 1) / cols);
            View first = grid.getChildAt(0);
            int cell = first != null && first.getHeight() > 0 ? first.getHeight() : U.dp(96);
            gl.height = rows * cell + grid.getPaddingTop() + grid.getPaddingBottom();
            gl.weight = 0;
            grid.setVisibility(rows == 0 ? GONE : VISIBLE);
        } else {
            gl.height = 0;
            gl.weight = 1;
        }
        grid.setLayoutParams(gl);
    }

    // ---------- Google suggestions + your search history ----------
    private String suggestFor = "";
    private final Runnable fetchSuggest = this::fetchSuggestions;

    /** Your past searches that match, then Google's autocomplete, like the Google search bar. */
    private void showSuggestions(String q) {
        removeCallbacks(fetchSuggest);
        suggestFor = q;
        suggest.removeAllViews();
        if (q.isEmpty()) {
            // tapped into an empty box: your recent searches, like Google
            if (search.hasFocus()) { int n = 0; for (String h : l.searchHistory()) { if (n++ >= 8) break; suggest.addView(suggestRow(h, true)); } }
            return;
        }
        String lq = q.toLowerCase(Locale.ROOT);
        int n = 0;
        for (String h : l.searchHistory()) {
            if (n >= 3) break;
            if (h.toLowerCase(Locale.ROOT).startsWith(lq)) { suggest.addView(suggestRow(h, true)); n++; }
        }
        if (l.prefs.sp.getBoolean("googleSuggest", true)) postDelayed(fetchSuggest, 150);
    }

    private void fetchSuggestions() {
        final String q = suggestFor;
        new Thread(() -> {
            java.util.List<String> out = new java.util.ArrayList<>();
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://suggestqueries.google.com/complete/search?client=firefox&ie=utf-8&oe=utf-8&hl="
                        + Locale.getDefault().getLanguage() + "&q=" + java.net.URLEncoder.encode(q, "UTF-8")).openConnection();
                c.setConnectTimeout(2500);
                c.setReadTimeout(2500);
                StringBuilder sb = new StringBuilder();
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), "UTF-8"))) {
                    for (String line; (line = r.readLine()) != null; ) sb.append(line);
                }
                org.json.JSONArray a = new org.json.JSONArray(sb.toString()).getJSONArray(1);
                for (int i = 0; i < a.length() && out.size() < 5; i++) out.add(a.getString(i));
            } catch (Exception ignored) { }
            post(() -> {
                if (!q.equals(suggestFor)) return; // you've typed more since
                java.util.Set<String> have = new java.util.HashSet<>();
                for (int i = 0; i < suggest.getChildCount(); i++) have.add(String.valueOf(suggest.getChildAt(i).getTag()).toLowerCase(Locale.ROOT));
                for (String s : out) {
                    if (s.equalsIgnoreCase(q) || !have.add(s.toLowerCase(Locale.ROOT))) continue;
                    suggest.addView(suggestRow(s, false));
                }
            });
        }).start();
    }

    /** One line: clock (history) or magnifier, the text with what you typed in normal weight, and an arrow that fills the box. */
    private View suggestRow(String text, boolean history) {
        U.Pal p = l.pal;
        LinearLayout row = new LinearLayout(l);
        row.setTag(text);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(U.dp(16), 0, U.dp(4), 0);
        ImageView iv = new ImageView(l);
        iv.setImageDrawable(history ? historyIcon(p.fg2) : new Glyph(Glyph.SEARCH, p.fg2));
        row.addView(iv, new LinearLayout.LayoutParams(U.dp(20), U.dp(20)));
        TextView t = U.text(l, 15.5f, p.fg, U.MEDIUM);
        String q = query();
        android.text.SpannableString ss = new android.text.SpannableString(text);
        if (text.toLowerCase(Locale.ROOT).startsWith(q.toLowerCase(Locale.ROOT)))
            ss.setSpan(new android.text.style.TypefaceSpan(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)), 0, q.length(), 0);
        t.setText(ss);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(U.dp(16), 0, U.dp(8), 0);
        row.addView(t, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        ImageView fill = new ImageView(l);
        fill.setImageDrawable(fillArrow(p.fg2));
        fill.setPadding(U.dp(13), U.dp(13), U.dp(13), U.dp(13));
        fill.setContentDescription("Fill in " + text);
        fill.setOnClickListener(v -> { search.setText(text + " "); search.setSelection(search.length()); });
        row.addView(fill, new LinearLayout.LayoutParams(U.dp(46), U.dp(46)));
        row.setOnClickListener(v -> { hideKeyboard(); l.webSearch(text); postDelayed(() -> search.setText(""), 600); });
        if (history) row.setOnLongClickListener(v -> { l.forgetSearch(text); suggest.removeView(row); U.toast(l, "Removed from history"); return true; });
        row.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, U.dp(48)));
        return row;
    }

    /** The ↖ "put it in the box" arrow. */
    private static android.graphics.drawable.Drawable fillArrow(int color) {
        return new android.graphics.drawable.Drawable() {
            final android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(android.graphics.Canvas c) {
                android.graphics.Rect b = getBounds();
                float s = b.width(), x = b.left, y = b.top;
                pt.setColor(color);
                pt.setStyle(android.graphics.Paint.Style.STROKE);
                pt.setStrokeWidth(s * 0.1f);
                pt.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                pt.setStrokeJoin(android.graphics.Paint.Join.ROUND);
                c.drawLine(x + s * 0.8f, y + s * 0.8f, x + s * 0.22f, y + s * 0.22f, pt);
                android.graphics.Path h = new android.graphics.Path();
                h.moveTo(x + s * 0.22f, y + s * 0.62f);
                h.lineTo(x + s * 0.22f, y + s * 0.22f);
                h.lineTo(x + s * 0.62f, y + s * 0.22f);
                c.drawPath(h, pt);
            }
            @Override public void setAlpha(int a) { }
            @Override public void setColorFilter(android.graphics.ColorFilter f) { }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
    }

    private static android.graphics.drawable.Drawable historyIcon(int color) {
        return new android.graphics.drawable.Drawable() {
            final android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(android.graphics.Canvas c) {
                android.graphics.Rect b = getBounds();
                float s = b.width(), cx = b.exactCenterX(), cy = b.exactCenterY();
                pt.setColor(color);
                pt.setStyle(android.graphics.Paint.Style.STROKE);
                pt.setStrokeWidth(s * 0.09f);
                pt.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                c.drawCircle(cx, cy, s * 0.4f, pt);
                c.drawLine(cx, cy, cx, cy - s * 0.22f, pt);
                c.drawLine(cx, cy, cx + s * 0.16f, cy + s * 0.1f, pt);
            }
            @Override public void setAlpha(int a) { }
            @Override public void setColorFilter(android.graphics.ColorFilter f) { }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
    }
    private static final Object[][] SETTINGS = {
            {"Wi-Fi", "wifi wlan internet network", android.provider.Settings.ACTION_WIFI_SETTINGS},
            {"Bluetooth", "bluetooth headphones pair", android.provider.Settings.ACTION_BLUETOOTH_SETTINGS},
            {"Mobile data", "data cellular sim network roaming", android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS},
            {"Hotspot & tethering", "hotspot tether share", "android.settings.TETHER_SETTINGS"},
            {"Display", "display brightness screen dark mode refresh resolution", android.provider.Settings.ACTION_DISPLAY_SETTINGS},
            {"Sound & vibration", "sound volume ringtone vibration", android.provider.Settings.ACTION_SOUND_SETTINGS},
            {"Notifications", "notifications alerts", android.provider.Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS},
            {"Do not disturb", "do not disturb dnd quiet silent", android.provider.Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS},
            {"Battery", "battery power saving charge", android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS},
            {"Apps", "apps applications default uninstall", android.provider.Settings.ACTION_APPLICATION_SETTINGS},
            {"Default apps", "default home launcher browser", android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS},
            {"Location", "location gps", android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS},
            {"Security & privacy", "security privacy lock password fingerprint face", android.provider.Settings.ACTION_SECURITY_SETTINGS},
            {"Accounts", "accounts sync google samsung", android.provider.Settings.ACTION_SYNC_SETTINGS},
            {"Storage", "storage space memory files", android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS},
            {"Accessibility", "accessibility font size zoom", android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS},
            {"Language & keyboard", "language keyboard input", android.provider.Settings.ACTION_LOCALE_SETTINGS},
            {"Date & time", "date time timezone clock", android.provider.Settings.ACTION_DATE_SETTINGS},
            {"Developer options", "developer options dpi usb debugging", android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS},
            {"About phone", "about phone software update version model", android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS},
            {"NFC & payments", "nfc tap pay contactless", android.provider.Settings.ACTION_NFC_SETTINGS},
            {"Wallpaper", "wallpaper background theme", Intent.ACTION_SET_WALLPAPER},
            {"Horizon settings", "horizon launcher home screen settings icons grid", "horizon"},
    };

    private void buildResults(String raw) {
        results.removeAllViews();
        String q = raw.toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return;
        U.Pal p = l.pal;
        // instant answers first: maths and unit conversions
        String ans = SmartSearch.calc(raw);
        if (ans == null) ans = SmartSearch.convert(raw);
        if (ans != null) {
            final String a = ans;
            results.addView(resultRow(new Glyph(Glyph.CHECK, p.accent), "= " + a, raw + " · tap to copy", () -> {
                android.content.ClipboardManager cm = l.getSystemService(android.content.ClipboardManager.class);
                if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Result", a.replaceAll("[^\\d.,\\-]", "")));
                U.toast(l, "Copied " + a);
            }));
        }
        results.addView(resultRow(new AiSpark(), "Ask AI", "\u201c" + raw + "\u201d with Gemini AI search", () -> l.aiSearch(raw)));
        if (raw.replaceAll("[\\s()+\\-]", "").matches("\\d{3,}")) {
            String num = raw.replaceAll("[^\\d+]", "");
            results.addView(resultRow(new Glyph(Glyph.INFO, p.accent), "Call " + raw, "Opens the phone app with this number",
                    () -> l.startSafely(new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + num)))));
        }
        int shownSettings = 0;
        for (Object[] st : SETTINGS) {
            String name = (String) st[0];
            if (!(name.toLowerCase(Locale.ROOT).contains(q) || ((String) st[1]).contains(q))) continue;
            String action = (String) st[2];
            results.addView(resultRow(new Glyph(Glyph.SETTINGS, p.accent), name, "Settings", () -> {
                if ("horizon".equals(action)) l.startSafely(new Intent(l, SettingsActivity.class));
                else l.startSafely(new Intent(action));
            }));
            if (++shownSettings == 2) break;
        }
        files(raw);
        messages(raw);
    }

    private boolean filesAllowed() {
        return android.os.Environment.isExternalStorageManager()
                || l.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** Files and photos whose names match, newest first. */
    private void files(String raw) {
        if (raw.length() < 2) return;
        U.Pal p = l.pal;
        if (!filesAllowed()) {
            if (!l.prefs.sp.getBoolean("filesAsked", false))
                results.addView(resultRow(new Glyph(Glyph.SEARCH, p.accent), "Search files too", "Allow Horizon to show matching photos, videos and music",
                        () -> { l.prefs.sp.edit().putBoolean("filesAsked", true).apply();
                            l.requestPermissions(new String[]{android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_AUDIO}, 32); }));
            return;
        }
        android.net.Uri uri = android.provider.MediaStore.Files.getContentUri("external");
        android.os.Bundle args = new android.os.Bundle();
        args.putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, android.provider.MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?");
        args.putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, new String[]{"%" + raw + "%"});
        args.putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, new String[]{android.provider.MediaStore.MediaColumns.DATE_MODIFIED});
        args.putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING);
        args.putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 2);
        try (android.database.Cursor c = l.getContentResolver().query(uri, new String[]{android.provider.MediaStore.MediaColumns._ID,
                android.provider.MediaStore.MediaColumns.DISPLAY_NAME, android.provider.MediaStore.MediaColumns.MIME_TYPE}, args, null)) {
            while (c != null && c.moveToNext()) {
                long id = c.getLong(0);
                String name = c.getString(1), mime = c.getString(2);
                android.net.Uri fu = android.content.ContentUris.withAppendedId(uri, id);
                results.addView(resultRow(new Glyph(Glyph.APPS, p.accent), name, "File" + (mime != null ? " · " + mime.replaceAll("/.*", "") : ""),
                        () -> l.startSafely(new Intent(Intent.ACTION_VIEW).setDataAndType(fu, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))));
            }
        } catch (Exception ignored) { }
    }

    /** Text messages that mention the search, newest first. */
    private void messages(String raw) {
        if (raw.length() < 3) return;
        U.Pal p = l.pal;
        if (l.checkSelfPermission(android.Manifest.permission.READ_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (!l.prefs.sp.getBoolean("smsAsked", false))
                results.addView(resultRow(new Glyph(Glyph.SEARCH, p.accent), "Search messages too", "Allow Horizon to show matching text messages (stays on your phone)",
                        () -> { l.prefs.sp.edit().putBoolean("smsAsked", true).apply(); l.requestPermissions(new String[]{android.Manifest.permission.READ_SMS}, 33); }));
            return;
        }
        try (android.database.Cursor c = l.getContentResolver().query(android.net.Uri.parse("content://sms"), new String[]{"address", "body"},
                "body LIKE ?", new String[]{"%" + raw + "%"}, "date DESC")) {
            int n = 0;
            while (c != null && c.moveToNext() && n < 2) {
                String addr = c.getString(0), body = c.getString(1);
                if (addr == null) continue;
                results.addView(resultRow(initial(addr), addr, body == null ? "" : body.replace('\n', ' '),
                        () -> l.startSafely(new Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:" + android.net.Uri.encode(addr))))));
                n++;
            }
        } catch (Exception ignored) { }
    }

    private void contacts(String raw) {
        U.Pal p = l.pal;
        if (l.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (raw.length() >= 2 && !l.prefs.sp.getBoolean("contactsAsked", false))
                results.addView(resultRow(new Glyph(Glyph.SEARCH, p.accent), "Search contacts too", "Allow Horizon to show matching people here",
                        () -> { l.prefs.sp.edit().putBoolean("contactsAsked", true).apply(); l.requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS}, 31); }));
            return;
        }
        android.net.Uri uri = android.net.Uri.withAppendedPath(android.provider.ContactsContract.Contacts.CONTENT_FILTER_URI, android.net.Uri.encode(raw));
        try (android.database.Cursor c = l.getContentResolver().query(uri,
                new String[]{android.provider.ContactsContract.Contacts._ID, android.provider.ContactsContract.Contacts.LOOKUP_KEY, android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY},
                null, null, null)) {
            int n = 0;
            while (c != null && c.moveToNext() && n < 3) {
                long id = c.getLong(0);
                String key = c.getString(1), name = c.getString(2);
                android.net.Uri cu = android.provider.ContactsContract.Contacts.getLookupUri(id, key);
                results.addView(resultRow(initial(name), name, "Contact", () -> l.startSafely(new Intent(Intent.ACTION_VIEW, cu))));
                n++;
            }
        } catch (Exception ignored) { }
    }

    private android.graphics.drawable.Drawable initial(String name) {
        U.Pal p = l.pal;
        String ch = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        return new android.graphics.drawable.Drawable() {
            final android.graphics.Paint bg = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG), tx = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(android.graphics.Canvas c) {
                android.graphics.Rect b = getBounds();
                bg.setColor(p.accent);
                c.drawCircle(b.exactCenterX(), b.exactCenterY(), b.width() / 2f, bg);
                tx.setColor(p.onAccent);
                tx.setTextSize(b.height() * 0.55f);
                tx.setTypeface(U.MEDIUM);
                tx.setTextAlign(android.graphics.Paint.Align.CENTER);
                c.drawText(ch, b.exactCenterX(), b.exactCenterY() - (tx.ascent() + tx.descent()) / 2, tx);
            }
            @Override public void setAlpha(int a) { }
            @Override public void setColorFilter(android.graphics.ColorFilter f) { }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
    }

    private View resultRow(android.graphics.drawable.Drawable icon, String title, String sub, Runnable r) {
        U.Pal p = l.pal;
        LinearLayout row = new LinearLayout(l);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(U.dp(16), U.dp(8), U.dp(16), U.dp(8));
        row.setBackground(U.round(p.sunk, 18));
        ImageView iv = new ImageView(l);
        iv.setImageDrawable(icon);
        row.addView(iv, new LinearLayout.LayoutParams(U.dp(26), U.dp(26)));
        LinearLayout tc = new LinearLayout(l);
        tc.setOrientation(LinearLayout.VERTICAL);
        tc.setPadding(U.dp(14), 0, 0, 0);
        TextView t = U.text(l, 15, p.fg, U.MEDIUM);
        t.setText(title);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tc.addView(t);
        TextView s = U.text(l, 12.5f, p.fg2, U.REGULAR);
        s.setText(sub);
        s.setSingleLine(true);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        s.setPadding(0, U.dp(2), 0, 0);
        tc.addView(s);
        row.addView(tc, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        row.setOnClickListener(v -> { hideKeyboard(); r.run(); postDelayed(() -> search.setText(""), 600); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, U.dp(56));
        lp.bottomMargin = U.dp(6);
        row.setLayoutParams(lp);
        return row;
    }

    // ---------- categories ----------
    private final LinearLayout chips;
    private final android.widget.HorizontalScrollView chipScroll;
    private String category;
    /** false: the App Library cards; true: a plain A–Z grid (one category, or every app). */
    private boolean list;
    private final LibraryView library;

    /** Back from a category returns to the library; returns true if it handled the back press. */
    boolean back() {
        if (list && query().isEmpty()) { list = false; category = null; filter(); return true; }
        return false;
    }
    private final java.util.Map<String, String> catOf = new java.util.HashMap<>();

    private String catOf(Apps.App a) {
        String c = catOf.get(a.key);
        if (c == null) { c = AppCategories.of(a); catOf.put(a.key, c); }
        return c;
    }

    private void buildChips(Set<String> hidden) {
        chips.removeAllViews();
        U.Pal p = l.pal;
        TextView back = U.text(l, 15, p.accent, U.MEDIUM);
        back.setText("\u2039  App Library");
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(U.dp(10), 0, U.dp(14), 0);
        back.setBackground(U.round(p.chip, 20));
        back.setOnClickListener(v -> back());
        chips.addView(back, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, U.dp(38)));
        TextView t = U.text(l, 20, p.fg, U.MEDIUM);
        t.setText(category == null ? "All apps" : category);
        t.setPadding(U.dp(14), 0, 0, 0);
        chips.addView(t);
    }

    private View chip(String name, String key) {
        U.Pal p = l.pal;
        boolean on = key == null ? category == null : key.equals(category);
        TextView t = U.text(l, 14, on ? p.onAccent : p.fg, U.MEDIUM);
        t.setText(name);
        t.setGravity(Gravity.CENTER);
        t.setPadding(U.dp(16), 0, U.dp(16), 0);
        t.setBackground(U.round(on ? p.accent : p.chip, 20));
        t.setContentDescription(name + (on ? ", selected" : ""));
        t.setOnClickListener(v -> { category = key; filter(); grid.setSelection(0); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        lp.rightMargin = U.dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private LinearLayout tray;
    private boolean claudeSet;
    private final Runnable refilter = this::filter;

    void filter() {
        if (!claudeSet && !l.apps.all.isEmpty()) { // the Claude icon is ready once apps have loaded
            claudeSet = true;
            ((ImageView) box.getChildAt(box.getChildCount() - 1)).setImageDrawable(l.claudeIcon());
        }
        String q = query().toLowerCase(Locale.ROOT);
        Set<String> hidden = l.prefs.hidden();
        List<Apps.App> starts = new ArrayList<>(), contains = new ArrayList<>();
        boolean lib = q.isEmpty() && !list;
        library.setVisibility(lib ? VISIBLE : GONE);
        library.recentStrip.setVisibility(lib ? VISIBLE : GONE);
        grid.setVisibility(lib ? GONE : VISIBLE);
        chipScroll.setVisibility(list && q.isEmpty() ? VISIBLE : GONE);
        if (lib) library.update(hidden); else if (list) buildChips(hidden);
        String cat = q.isEmpty() ? category : null; // search always covers every app
        for (Apps.App a : l.apps.all) {
            if (hidden.contains(a.key)) continue;
            if (cat != null && !cat.equals(catOf(a))) continue;
            String lab = a.label.toLowerCase(Locale.ROOT);
            if (q.isEmpty() || lab.startsWith(q)) starts.add(a);
            else if (lab.contains(q) || lab.replace(" ", "").contains(q)) contains.add(a);
        }
        starts.addAll(contains);
        shown = starts;
        adapter.rebuildSections();
        adapter.notifyDataSetChanged();
        clear.setVisibility(q.isEmpty() ? GONE : VISIBLE);
        web.setVisibility(q.isEmpty() ? GONE : VISIBLE);
        web.setText("Search the web for “" + query() + "”");
        buildResults(query());
        showSuggestions(query());
        layoutForSearch(!q.isEmpty());
        if (!q.isEmpty()) grid.post(() -> layoutForSearch(!query().isEmpty())); // once the icons have their real height
        if (!q.isEmpty()) foundScroll.scrollTo(0, 0);
        empty.setVisibility(!q.isEmpty() && shown.isEmpty() ? VISIBLE : GONE);
        empty.setText("No apps match “" + query() + "”");
        grid.setFastScrollEnabled(q.isEmpty() && category == null);
        empty.setVisibility(!q.isEmpty() && shown.isEmpty() ? VISIBLE : GONE);
    }

    void refreshBadges() { adapter.notifyDataSetChanged(); }

    // ---------- open / close ----------

    float progress() { return progress; }
    boolean isOpen() { return progress > 0.5f; }

    void prepare() {
        if (anim != null) anim.cancel();
        if (progress == 0f) filter();
        setVisibility(VISIBLE);
        setTranslationY(getHeight() > 0 ? getHeight() : l.root.getHeight());
    }

    private boolean warm;

    /** After startup: let the closed drawer lay out off-screen so its App Library is built before the first open. */
    void prewarm() {
        warm = true;
        if (progress == 0f) {
            int h = getHeight() > 0 ? getHeight() : l.root.getHeight();
            setTranslationY(h);
            setVisibility(INVISIBLE);
        }
    }

    void setProgress(float p) {
        progress = p;
        setVisibility(p > 0f ? VISIBLE : warm ? INVISIBLE : GONE); // invisible = laid out, so the App Library is ready
        int h = getHeight() > 0 ? getHeight() : l.root.getHeight();
        setTranslationY((1f - p) * h);
        l.onDrawerProgress(p);
    }

    void animateTo(float target) { animateTo(target, 0f); }

    /** iOS-style spring: carries on with the finger's speed (velocity in drawer-heights per second) and settles. */
    private final Spring spring = new Spring(420f, 0.88f);

    void animateTo(float target, float velocity) {
        if (anim != null) anim.cancel();
        if (target > 0f) setVisibility(VISIBLE);
        spring.start(progress, target, velocity, v -> setProgress(U.clamp(v, 0f, 1.02f) > 1f ? 1f : Math.max(0f, v)), () -> {
            setProgress(target);
            performHapticFeedback(android.view.HapticFeedbackConstants.GESTURE_END);
            if (target == 0f) { hideKeyboard(); list = false; category = null; search.setText(""); grid.setSelection(0); library.scrollTo(0, 0); }
            if (target == 1f && l.prefs.drawerKeyboard() && !search.hasFocus()) focusSearch();
        });
    }

    void open(boolean focusSearch) {
        prepare();
        animateTo(1f);
        if (focusSearch) focusSearch();
    }

    private void focusSearch() {
        search.requestFocus();
        search.postDelayed(() -> {
            InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
            if (imm != null) imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
        }, 120);
    }

    void close() { hideKeyboard(); animateTo(0f); }

    private void hideKeyboard() {
        InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
        search.clearFocus();
    }

    // ---------- swipe down to close when the list is at the top ----------

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dx0 = e.getRawX(); dy0 = e.getRawY(); closing = false; // screen coordinates: this view moves with the finger
                break;
            case MotionEvent.ACTION_MOVE:
                float dy = e.getRawY() - dy0, dx = e.getRawX() - dx0;
                View lst = library.getVisibility() == VISIBLE ? library : grid;
                boolean atTop = !lst.canScrollVertically(-1) || e.getY() < lst.getTop();
                if (dy > slop && dy > Math.abs(dx) * 1.3f && atTop) {
                    closing = true;
                    vt = VelocityTracker.obtain();
                    hideKeyboard();
                    return true;
                }
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!closing) return true;
        vt.addMovement(e);
        float p = U.clamp(1f - (e.getRawY() - dy0) / (getHeight() * 0.7f), 0f, 1f);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                setProgress(p);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                vt.computeCurrentVelocity(1000);
                animateTo(vt.getYVelocity() > 600 || p < 0.75f ? 0f : 1f, -vt.getYVelocity() / Math.max(1f, getHeight() * 0.7f));
                vt.recycle();
                vt = null;
                closing = false;
                break;
        }
        return true;
    }

    // ---------- grid adapter with A–Z sections ----------

    private final class Adapter extends BaseAdapter implements SectionIndexer {
        private String[] sections = new String[0];
        private int[] sectionPos = new int[0];

        void rebuildSections() {
            List<String> s = new ArrayList<>();
            List<Integer> p = new ArrayList<>();
            for (int i = 0; i < shown.size(); i++) {
                String lab = shown.get(i).label;
                String k = lab.isEmpty() || !Character.isLetter(lab.charAt(0)) ? "#" : lab.substring(0, 1).toUpperCase(Locale.ROOT);
                if (s.isEmpty() || !s.get(s.size() - 1).equals(k)) { s.add(k); p.add(i); }
            }
            sections = s.toArray(new String[0]);
            sectionPos = new int[p.size()];
            for (int i = 0; i < p.size(); i++) sectionPos[i] = p.get(i);
        }

        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public Object[] getSections() { return sections; }
        @Override public int getPositionForSection(int s) { return sectionPos.length == 0 ? 0 : sectionPos[Math.max(0, Math.min(sectionPos.length - 1, s))]; }
        @Override public int getSectionForPosition(int pos) {
            for (int i = sectionPos.length - 1; i >= 0; i--) if (sectionPos[i] <= pos) return i;
            return 0;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            IconView v = convert instanceof IconView && ((IconView) convert).iconPx == l.iconPx ? (IconView) convert : null;
            if (v == null) {
                v = new IconView(l, l.iconPx);
                v.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, l.iconPx + U.dp(34)));
                v.setOnClickListener(x -> { IconView iv = (IconView) x; l.launchApp(l.apps.get(iv.item.key()), iv); });
                v.setOnLongClickListener(x -> l.onDrawerLongClick((IconView) x));
            }
            Apps.App a = shown.get(i);
            Item it = Item.app(a);
            it.container = Item.DRAWER;
            v.setLabelColor(l.pal.fg, false);
            v.bind(it, l.apps.icon(a), a.label);
            v.setVisibility(VISIBLE);
            v.setBadge(Badges.get(a.pkg, a.serial), l.prefs.badges());
            return v;
        }
    }
}
