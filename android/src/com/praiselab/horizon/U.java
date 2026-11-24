package com.praiselab.horizon;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/** Small shared helpers: units, palette (Material You when available), drawing utilities. */
final class U {
    static float density = 1f;
    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);

    private U() { }

    static void init(Context c) { density = c.getResources().getDisplayMetrics().density; }

    /** A translucent colour laid over an opaque one, as a single opaque colour. */
    static int over(int base, int top) {
        float a = ((top >>> 24) & 255) / 255f;
        int r = Math.round(((top >> 16) & 255) * a + ((base >> 16) & 255) * (1 - a));
        int g = Math.round(((top >> 8) & 255) * a + ((base >> 8) & 255) * (1 - a));
        int b = Math.round((top & 255) * a + (base & 255) * (1 - a));
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    /** A faint shadow under a card, lifting it off the wallpaper (drawn by the system, no extra work per frame). */
    static void softShadow(android.view.View v) {
        v.setElevation(dp(6));
        if (Build.VERSION.SDK_INT >= 28) {
            v.setOutlineAmbientShadowColor(0x66000000);
            v.setOutlineSpotShadowColor(0x99000000);
        }
    }

    static int dp(float v) { return Math.round(v * density); }

    static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    /** True when the active look is dark (follows the phone unless a mode is chosen in Horizon). */
    static boolean dark(Context c) { return pal(c).dark; }

    static int alpha(int color, float a) { return (color & 0x00ffffff) | (Math.round(a * 255) << 24); }

    /** Colors and shapes for the active look. Every surface in the launcher draws from this. */
    static final class Pal {
        String style;
        boolean dark, black, colorTiles, lightTileText;
        int surface, solid, sunk, chip, fg, fg2, fg3, accent, onAccent, line, danger, scrim;
        int tile, tileFg, tileFg2, rim;
        float radius, tileRadius;
    }

    static final String[] STYLES = {"material", "glass", "spatial", "pixel", "tiles"};
    static final String[] STYLE_NAMES = {"Material You", "Glass", "Spatial", "Modern Android", "Tiles"};
    static final String[] MODES = {"system", "light", "dark", "black"};
    static final String[] MODE_NAMES = {"Auto", "Light", "Dark", "Black"};

    private static int sys(Context c, int id, int fallback) {
        return Build.VERSION.SDK_INT >= 31 ? c.getColor(id) : fallback;
    }

    static Pal pal(Context c) {
        Prefs pr = new Prefs(c);
        String style = pr.style(), mode = pr.mode();
        boolean sysDark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        Pal p = new Pal();
        p.style = style;
        Sq.glassy = "spatial".equals(style);
        Glass.enabled = pr.sp.getBoolean("liquidGlass", true);
        Apps.glassy = Sq.glassy;
        p.black = mode.equals("black");
        p.dark = p.black || mode.equals("dark") || (mode.equals("system") && sysDark);
        boolean d = p.dark;
        int n10 = sys(c, android.R.color.system_neutral1_10, 0xfffafbfd), n50 = sys(c, android.R.color.system_neutral1_50, 0xffeef0f5),
            n900 = sys(c, android.R.color.system_neutral1_900, 0xff1b1d23), n2_50 = sys(c, android.R.color.system_neutral2_50, 0xffeef1f6),
            n2_100 = sys(c, android.R.color.system_neutral2_100, 0xffe4e9f1), n2_200 = sys(c, android.R.color.system_neutral2_200, 0xffc3c8d3),
            n2_400 = sys(c, android.R.color.system_neutral2_400, 0xff9aa1b0), n2_500 = sys(c, android.R.color.system_neutral2_500, 0xff656d7e),
            n2_700 = sys(c, android.R.color.system_neutral2_700, 0xff454c5b), n2_800 = sys(c, android.R.color.system_neutral2_800, 0xff2a2d36),
            n2_900 = sys(c, android.R.color.system_neutral2_900, 0xff14161b),
            a1_100 = sys(c, android.R.color.system_accent1_100, 0xffd3e3fd), a1_200 = sys(c, android.R.color.system_accent1_200, 0xff8ab4f8),
            a1_600 = sys(c, android.R.color.system_accent1_600, 0xff1a66d6), a1_800 = sys(c, android.R.color.system_accent1_800, 0xff0b1b33),
            a1_900 = sys(c, android.R.color.system_accent1_900, 0xff041e49),
            a2_50 = sys(c, android.R.color.system_accent2_50, 0xffeef3fb), a2_100 = sys(c, android.R.color.system_accent2_100, 0xffdbe4f3),
            a2_800 = sys(c, android.R.color.system_accent2_800, 0xff2c3442), a2_900 = sys(c, android.R.color.system_accent2_900, 0xff171c26);

        p.fg = d ? n50 : 0xff17191e;
        p.fg2 = d ? n2_200 : n2_700;
        p.fg3 = d ? n2_400 : n2_500;
        p.accent = d ? a1_200 : a1_600;
        p.onAccent = d ? a1_800 : 0xffffffff;
        p.line = d ? 0x1affffff : 0x17000000;
        p.danger = d ? 0xfff28b82 : 0xffc5221f;
        p.scrim = d ? 0x99000000 : 0x66000000;
        p.radius = 26;
        p.tileRadius = 22;
        p.rim = 0;

        switch (style) {
            case "glass": {
                int base = d ? (p.black ? 0xff000000 : 0xff1c1e22) : 0xffffffff;
                p.solid = alpha(base, d ? 0.90f : 0.93f);
                p.surface = alpha(base, d ? 0.62f : 0.70f);
                p.tile = alpha(base, d ? 0.48f : 0.58f);
                p.rim = d ? 0x33ffffff : 0x99ffffff;
                p.chip = d ? 0x1fffffff : 0x14000000;
                p.sunk = d ? 0x14ffffff : 0x0d000000;
                p.radius = 26;
                p.tileRadius = 22;
                break;
            }
            case "spatial": {
                // visionOS glass: warm gray, translucent, white type, light-catching edges (drawn by Sq)
                int base = p.black ? 0xff1a1b1f : d ? 0xff3a3d44 : 0xff6a6f78;
                p.solid = alpha(base, 0.96f);
                p.surface = alpha(base, d ? 0.62f : 0.66f);
                p.tile = alpha(base, d ? 0.58f : 0.62f);
                p.rim = 0;
                p.fg = 0xffffffff;
                p.fg2 = 0xdcffffff;
                p.fg3 = 0xb3ffffff;
                p.chip = 0x26ffffff;
                p.sunk = 0x17ffffff;
                p.line = 0x24ffffff;
                p.accent = 0xff0a84ff;
                p.onAccent = 0xffffffff;
                p.danger = 0xffff8a80;
                p.radius = 26;
                p.tileRadius = 22;
                break;
            }
            case "material": {
                p.solid = p.black ? 0xff000000 : d ? n900 : n10;
                p.surface = p.black ? 0xf2000000 : alpha(d ? a2_900 : a2_50, 0.97f);
                p.tile = p.black ? 0xff141519 : alpha(d ? a2_800 : a1_100, 0.96f);
                p.tileFg = d ? a1_100 : a1_900;
                p.chip = p.black ? 0xff1c1d22 : d ? a2_800 : a2_100;
                p.sunk = p.black ? 0xff0b0b0d : d ? n2_900 : n2_50;
                p.radius = 26;
                p.tileRadius = 22;
                break;
            }
            case "tiles": {
                p.solid = p.black ? 0xff000000 : d ? 0xff1f1f1f : 0xfff2f2f2;
                p.surface = alpha(p.solid, 0.97f);
                p.tile = a1_600 | 0xff000000;
                p.colorTiles = true;
                p.lightTileText = true;
                p.chip = p.black ? 0xff1a1a1a : d ? 0xff333333 : 0xffe0e0e0;
                p.sunk = p.black ? 0xff0d0d0d : d ? 0xff2a2a2a : 0xffe8e8e8;
                p.radius = 6;
                p.tileRadius = 3;
                break;
            }
            default: { // "pixel": modern Android, near-solid neutral surfaces
                p.solid = p.black ? 0xff000000 : d ? n900 : n10;
                p.surface = p.black ? 0xf5000000 : alpha(p.solid, 0.96f);
                p.tile = p.black ? 0xff121212 : alpha(d ? n2_800 : n2_50, 0.94f);
                p.chip = p.black ? 0xff1c1c1c : d ? n2_800 : n2_100;
                p.sunk = p.black ? 0xff0a0a0a : d ? n2_900 : n2_50;
                p.radius = 26;
                p.tileRadius = 22;
                break;
            }
        }
        String op = pr.tileOpacity();
        if (!"auto".equals(op)) {
            try { p.tile = alpha(p.tile, Integer.parseInt(op) / 100f); } catch (NumberFormatException ignored) { }
        }
        if (p.tileFg == 0) p.tileFg = p.lightTileText ? 0xffffffff : p.fg;
        p.tileFg2 = p.lightTileText ? 0xd9ffffff : alpha(p.tileFg, 0.72f);
        if (style.equals("spatial")) { p.tileFg = 0xffffffff; p.tileFg2 = 0xd0ffffff; }
        return p;
    }

    /** Continuous-corner surface (Apple style). */
    static Sq round(int color, float radiusDp) { return new Sq(color, dp(radiusDp)); }

    /** Spacing system: outer margin and the single gap used between every tile, widget and card. */
    static int margin() { return dp(20); }
    static int gap() { return dp(14); }
    /** Corner radii: cards and widgets, menus and sheets. */
    static final float R_CARD = 22, R_MENU = 26, R_SHEET = 34;

    static TextView text(Context c, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    static Bitmap bitmap(Drawable d, int w, int h) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        return b;
    }

    static Bitmap snapshot(View v) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, v.getWidth()), Math.max(1, v.getHeight()), Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        return b;
    }

    /** Bounds of a view relative to an ancestor, including any scroll in between. */
    static Rect rectIn(View v, View ancestor) {
        int[] a = new int[2], b = new int[2];
        v.getLocationOnScreen(a);
        ancestor.getLocationOnScreen(b);
        int x = a[0] - b[0], y = a[1] - b[1];
        return new Rect(x, y, x + v.getWidth(), y + v.getHeight());
    }

    static void toast(Context c, String s) { Toast.makeText(c, s, Toast.LENGTH_SHORT).show(); }
}
