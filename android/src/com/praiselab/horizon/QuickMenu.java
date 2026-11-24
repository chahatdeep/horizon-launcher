package com.praiselab.horizon;

import android.app.NotificationManager;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Date;

/** Swipe-down Control menu: quick toggles, brightness, music, and the Look switcher. Styled by the active look. */
final class QuickMenu extends FrameLayout {
    private static boolean torchOn;
    private static String torchId;
    private final Launcher l;
    private final LinearLayout col;
    private final ScrollView scroll;
    private final CameraManager cams;
    private final CameraManager.TorchCallback torchCb = new CameraManager.TorchCallback() {
        @Override public void onTorchModeChanged(String id, boolean on) {
            if (id.equals(torchId)) { torchOn = on; post(QuickMenu.this::build); }
        }
    };
    private float downY;

    QuickMenu(Launcher l) {
        super(l);
        this.l = l;
        cams = l.getSystemService(CameraManager.class);
        if (torchId == null) torchId = findTorch();
        setBackgroundColor(0xcc000000);
        setClickable(true);
        setOnClickListener(v -> l.closeQuickMenu());
        scroll = new ScrollView(l);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        col = new LinearLayout(l);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(U.dp(10), U.dp(10), U.dp(10), U.dp(10));
        col.setClickable(true);
        scroll.addView(col);
        LayoutParams slp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP);
        slp.setMargins(U.dp(10), l.insetTop + U.dp(8), U.dp(10), U.dp(10));
        addView(scroll, slp);
        build();
        scroll.setTranslationY(-U.dp(60));
        scroll.setAlpha(0f);
        scroll.animate().translationY(0).alpha(1f).setDuration(220).start();
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); try { cams.registerTorchCallback(torchCb, null); } catch (Exception ignored) { } }
    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); try { cams.unregisterTorchCallback(torchCb); } catch (Exception ignored) { } }

    private String findTorch() {
        try {
            for (String id : cams.getCameraIdList()) {
                Boolean f = cams.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (f != null && f) return id;
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** A section of the panel: same inner padding everywhere, subtle inset surface, continuous corners. */
    private LinearLayout card() {
        U.Pal p = l.pal;
        LinearLayout c = new LinearLayout(l);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(U.round(U.alpha(p.fg, p.dark ? 0.06f : 0.05f), U.R_CARD));
        c.setPadding(U.dp(14), U.dp(14), U.dp(14), U.dp(14));
        c.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = U.dp(10);
        col.addView(c, lp);
        return c;
    }

    void build() {
        col.removeAllViews();
        U.Pal p = l.pal;
        Sq panel = U.round(menuColor(p), U.R_SHEET);
        if (p.rim != 0) panel.setStroke(U.dp(1), p.rim);
        col.setBackground(panel);
        Sq.clip(col, U.dp(U.R_SHEET));

        // header: time, date, battery, settings
        LinearLayout head = card();
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout tcol = new LinearLayout(l);
        tcol.setOrientation(LinearLayout.VERTICAL);
        TextView time = U.text(l, 34, p.fg, U.LIGHT);
        time.setText(DateFormat.format(DateFormat.is24HourFormat(l) ? "H:mm" : "h:mm", new Date()));
        tcol.addView(time);
        TextView date = U.text(l, 14, p.fg2, U.MEDIUM);
        BatteryManager bm = l.getSystemService(BatteryManager.class);
        int batt = bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
        date.setText(DateFormat.format("EEEE, MMMM d", new Date()) + (batt > 0 ? "  ·  " + batt + "%" : ""));
        date.setPadding(0, U.dp(4), 0, 0);
        tcol.addView(date);
        head.addView(tcol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        head.addView(roundButton(Glyph.SETTINGS, "Horizon settings", () -> { l.closeQuickMenu(); l.startSafely(new Intent(l, SettingsActivity.class)); }));

        // quick toggles
        LinearLayout tiles = card();
        LinearLayout r1 = new LinearLayout(l), r2 = new LinearLayout(l);
        tiles.addView(r1);
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        r2lp.topMargin = U.dp(10);
        tiles.addView(r2, r2lp);
        WifiManager wm = l.getApplicationContext().getSystemService(WifiManager.class);
        boolean wifi = wm != null && wm.isWifiEnabled();
        boolean bt = false;
        try { BluetoothManager b = l.getSystemService(BluetoothManager.class); bt = b != null && b.getAdapter() != null && b.getAdapter().isEnabled(); } catch (Exception ignored) { }
        NotificationManager nm = l.getSystemService(NotificationManager.class);
        boolean dnd = nm != null && nm.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL;
        LocationManager lm = l.getSystemService(LocationManager.class);
        boolean loc = lm != null && lm.isLocationEnabled();

        r1.addView(toggle(Glyph.WIFI, "Wi-Fi", wifi, () -> open(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))));
        r1.addView(toggle(Glyph.BLUETOOTH, "Bluetooth", bt, () -> open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))));
        r1.addView(toggle(Glyph.FLASH, "Flashlight", torchOn, this::torch));
        r1.addView(toggle(Glyph.MOON, "Do not disturb", dnd, this::dnd));
        r2.addView(toggle(Glyph.LOCATION, "Location", loc, () -> open(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))));
        r2.addView(toggle(Glyph.BELL, "Notifications", false, () -> { l.closeQuickMenu(); l.expandShade(); }));
        r2.addView(toggle(Glyph.WALLPAPER, "Wallpaper", false, () -> open(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "Set wallpaper"))));
        r2.addView(toggle(Glyph.WIDGETS, "Widgets", false, () -> { l.closeQuickMenu(); l.openWidgets(null, Item.PANEL); }));

        // brightness
        LinearLayout br = new LinearLayout(l);
        br.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams brlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, U.dp(48));
        brlp.topMargin = U.dp(12);
        br.setBackground(U.round(p.chip, 24));
        br.setPadding(U.dp(14), 0, U.dp(6), 0);
        ImageView sun = new ImageView(l);
        sun.setImageDrawable(new Glyph(Glyph.SUN, p.fg2));
        br.addView(sun, new LinearLayout.LayoutParams(U.dp(22), U.dp(22)));
        SeekBar seek = new SeekBar(l);
        seek.setMax(255);
        seek.setContentDescription("Screen brightness");
        try { seek.setProgress(Settings.System.getInt(l.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS)); } catch (Exception ignored) { }
        seek.setProgressTintList(ColorStateList.valueOf(p.accent));
        seek.setThumbTintList(ColorStateList.valueOf(p.accent));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(p.fg3));
        seek.setOnTouchListener((v, e) -> {
            if (!Settings.System.canWrite(l)) {
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    U.toast(l, "Allow Horizon to change system settings to adjust brightness here");
                    open(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + l.getPackageName())));
                }
                return true;
            }
            return false;
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                if (!fromUser || !Settings.System.canWrite(l)) return;
                try {
                    Settings.System.putInt(l.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    Settings.System.putInt(l.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(1, v));
                } catch (Exception ignored) { }
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        br.addView(seek, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        tiles.addView(br, brlp);

        // music
        NowPlayingCard media = new NowPlayingCard(l);
        media.solid = menuColor(p);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, U.dp(170));
        mlp.bottomMargin = U.dp(10);
        col.addView(media, mlp);
        media.solid = U.alpha(p.fg, p.dark ? 0.06f : 0.05f) == 0 ? 0 : (menuColor(p) & 0x00ffffff) | 0xff000000;

        // look switcher
        LinearLayout look = card();
        TextView lh = U.text(l, 13, p.fg2, U.MEDIUM);
        lh.setText("LOOK");
        lh.setLetterSpacing(0.08f);
        lh.setPadding(U.dp(2), 0, 0, U.dp(10));
        look.addView(lh);
        String style = l.prefs.style(), mode = l.prefs.mode();
        // one even row: a round swatch per look, its name centred below, a ring on the active one
        LinearLayout styles = new LinearLayout(l);
        styles.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < U.STYLES.length; i++) {
            final String key = U.STYLES[i];
            styles.addView(styleChip(U.STYLE_NAMES[i], key, key.equals(style)));
        }
        LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stl.bottomMargin = U.dp(12);
        styles.setLayoutParams(stl);
        look.addView(styles);
        LinearLayout seg = new LinearLayout(l);
        seg.setBackground(U.round(p.chip, 22));
        seg.setPadding(U.dp(4), U.dp(4), U.dp(4), U.dp(4));
        for (int i = 0; i < U.MODES.length; i++) {
            final String key = U.MODES[i];
            boolean on = key.equals(mode);
            TextView b = U.text(l, 14, on ? p.onAccent : p.fg, U.MEDIUM);
            b.setText(U.MODE_NAMES[i]);
            b.setGravity(Gravity.CENTER);
            if (on) b.setBackground(U.round(p.accent, 18));
            b.setOnClickListener(v -> l.setLook(null, key));
            seg.addView(b, new LinearLayout.LayoutParams(0, U.dp(38), 1));
        }
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = U.dp(4);
        look.addView(seg, slp);

        View grab = new View(l);
        grab.setBackground(U.round(U.alpha(p.fg, 0.35f), 3));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(U.dp(40), U.dp(5));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.topMargin = U.dp(2);
        glp.bottomMargin = U.dp(2);
        col.addView(grab, glp);
    }

    /** Menus favor readability: opaque, except a light frost in the Glass look. */
    static int menuColor(U.Pal p) { return "glass".equals(p.style) ? U.alpha(p.solid, 0.96f) : p.solid | 0xff000000; }

    private View styleChip(String name, String key, boolean on) {
        U.Pal p = l.pal;
        LinearLayout c = new LinearLayout(l);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(0, U.dp(4), 0, U.dp(4));
        android.widget.FrameLayout ring = new android.widget.FrameLayout(l);
        GradientDrawable rg = new GradientDrawable();
        rg.setShape(GradientDrawable.OVAL);
        rg.setColor(0);
        rg.setStroke(U.dp(2.5f), on ? p.accent : 0);
        ring.setBackground(rg);
        View sw = new View(l);
        GradientDrawable g = swatch(key);
        g.setShape(GradientDrawable.OVAL);
        sw.setBackground(g);
        android.widget.FrameLayout.LayoutParams swl = new android.widget.FrameLayout.LayoutParams(U.dp(40), U.dp(40), Gravity.CENTER);
        ring.addView(sw, swl);
        c.addView(ring, new LinearLayout.LayoutParams(U.dp(52), U.dp(52)));
        TextView t = U.text(l, 12, on ? p.fg : p.fg2, on ? U.MEDIUM : U.REGULAR);
        t.setText(name.replace("Modern Android", "Android").replace("Material You", "Material"));
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = U.dp(6);
        c.addView(t, tl);
        c.setContentDescription(name + " look" + (on ? ", selected" : ""));
        c.setOnClickListener(v -> l.setLook(key, null));
        c.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return c;
    }

    private GradientDrawable swatch(String key) {
        int[] colors;
        switch (key) {
            case "glass": colors = new int[]{0xccffffff, 0x66ffffff}; break;
            case "spatial": colors = new int[]{0xff6c727c, 0xff3a3e45}; break;
            case "material": colors = new int[]{0xffd3e3fd, 0xff4f6f9f}; break;
            case "tiles": colors = new int[]{0xff0078d7, 0xffe81123}; break;
            default: colors = new int[]{0xfff1f3f6, 0xff2a2d36};
        }
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        g.setStroke(U.dp(1), 0x33ffffff);
        return g;
    }

    private View toggle(int glyph, String label, boolean on, Runnable r) {
        U.Pal p = l.pal;
        LinearLayout t = new LinearLayout(l);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView iv = new ImageView(l);
        iv.setImageDrawable(new Glyph(glyph, on ? p.onAccent : p.fg));
        iv.setPadding(U.dp(15), U.dp(15), U.dp(15), U.dp(15));
        iv.setBackground(U.round(on ? p.accent : p.chip, Math.min(p.radius, 22)));
        t.addView(iv, new LinearLayout.LayoutParams(U.dp(56), U.dp(56)));
        TextView tv = U.text(l, 11.5f, p.fg2, U.MEDIUM);
        tv.setText(label);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setPadding(0, U.dp(6), 0, 0);
        t.addView(tv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        t.setContentDescription(label + (on ? ", on" : ""));
        t.setOnClickListener(v -> r.run());
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return t;
    }

    private View roundButton(int glyph, String desc, Runnable r) {
        ImageView b = new ImageView(l);
        b.setImageDrawable(new Glyph(glyph, l.pal.fg));
        b.setPadding(U.dp(11), U.dp(11), U.dp(11), U.dp(11));
        b.setBackground(U.round(l.pal.chip, 22));
        b.setContentDescription(desc);
        b.setOnClickListener(v -> r.run());
        b.setLayoutParams(new LinearLayout.LayoutParams(U.dp(44), U.dp(44)));
        return b;
    }

    private void open(Intent i) {
        l.closeQuickMenu();
        l.startSafely(i);
    }

    private void torch() {
        if (torchId == null) { U.toast(l, "This phone has no flashlight"); return; }
        try { cams.setTorchMode(torchId, !torchOn); } catch (Exception e) { U.toast(l, "The flashlight is busy"); }
    }

    private void dnd() {
        NotificationManager nm = l.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (!nm.isNotificationPolicyAccessGranted()) {
            U.toast(l, "Allow Horizon to manage Do Not Disturb, then tap again");
            open(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            return;
        }
        boolean on = nm.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL;
        nm.setInterruptionFilter(on ? NotificationManager.INTERRUPTION_FILTER_ALL : NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        build();
    }

    // a long swipe up anywhere on the menu closes it (when there is nothing more to scroll)
    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) downY = e.getY();
        if (e.getActionMasked() == MotionEvent.ACTION_UP && e.getY() - downY < -U.dp(120) && !scroll.canScrollVertically(1)) {
            l.closeQuickMenu();
            return true;
        }
        return super.dispatchTouchEvent(e);
    }
}
