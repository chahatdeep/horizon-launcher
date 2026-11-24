package com.praiselab.horizon;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Launcher settings. Every write bumps a stamp so the home screen knows to re-apply. */
final class Prefs {
    final SharedPreferences sp;

    Prefs(Context c) { sp = c.getSharedPreferences("launcher", Context.MODE_PRIVATE); }

    /** Home tile grid columns: 6 (small tiles) or 4 (large tiles). */
    int cols() { return sp.getInt("cols", 5); }
    String style() { return sp.getString("style", "spatial"); }
    String mode() { return sp.getString("mode", "system"); }
    boolean previews() { return sp.getBoolean("previews", true); }
    /** Swipe down on Home: menu (Control menu), shade (notifications), search, or none. */
    String swipeAction() { return sp.getString("swipeAction", "menu"); }
    float iconScale() { return sp.getFloat("iconScale", 1f); }
    boolean themed() { String s = sp.getString("iconStyle", "color"); return "themed".equals(s) || "themedAll".equals(s); }
    /** Themed look for every app: icons without a themed version are tinted to match. */
    boolean themedAll() { return "themedAll".equals(sp.getString("iconStyle", "color")); }
    boolean labels() { return sp.getBoolean("labels", true); }
    boolean searchBar() { return sp.getBoolean("searchBar", true); }
    boolean feed() { return sp.getBoolean("feed", true); }
    String badges() { return sp.getString("badges", "count"); }
    /** Double-tap on empty Home: none, lock, menu (Control menu) or drawer. */
    String doubleTapAction() { return sp.getString("doubleTapAction", "none"); }
    boolean doubleTap() { return !"none".equals(doubleTapAction()); }
    boolean parallax() { return sp.getBoolean("parallax", true); }
    /** Wallpaper dimming behind Home, in percent (0-40), for readability on busy wallpapers. */
    int dim() { return sp.getInt("dim", 0); }
    /** "auto" uses the look's own tile transparency; otherwise an opacity percent. */
    String tileOpacity() { return sp.getString("tileOpacity", "auto"); }
    String iconShape() { return sp.getString("iconShape", "circle"); }
    int drawerCols() { return sp.getInt("drawerCols", 5); }
    boolean drawerKeyboard() { return sp.getBoolean("drawerKeyboard", false); }
    boolean autoAdd() { return sp.getBoolean("autoAdd", true); }
    boolean locked() { return sp.getBoolean("locked", false); }
    boolean tileLabels() { return sp.getBoolean("tileLabels", true); }
    int dockCount() { return sp.getInt("dockCount", 4); }
    /** Home folders open "inline" (Windows Phone style) or as a "popup". */
    String folderStyle() { return sp.getString("folderStyle", "inline"); }
    boolean glance() { return sp.getBoolean("glance", true); }
    boolean statusBar() { return sp.getBoolean("statusBar", true); }
    /** Weather units on Home: C or F. */
    String units() { return sp.getString("units", "C"); }
    /** Number of widget pages to the right of Home. */
    int panelPages() { return Math.max(1, sp.getInt("panelPages", 1)); }
    /** Horizon-only text size and content size, in percent, on top of the phone's own font and display size. */
    int textPct() { try { return Integer.parseInt(sp.getString("textSize", "90")); } catch (Exception e) { return 90; } }
    int uiPct() { try { return Integer.parseInt(sp.getString("uiSize", "100")); } catch (Exception e) { return 100; } }

    /** Wraps a context so everything Horizon draws (widgets included) uses the chosen sizes. */
    static Context scaled(Context base) {
        Prefs p = new Prefs(base);
        android.content.res.Configuration c = new android.content.res.Configuration(base.getResources().getConfiguration());
        c.fontScale = c.fontScale * p.textPct() / 100f;
        c.densityDpi = Math.max(120, Math.round(c.densityDpi * p.uiPct() / 100f));
        return base.createConfigurationContext(c);
    }

    long stamp() { return sp.getLong("stamp", 0); }

    Set<String> hidden() { return new HashSet<>(sp.getStringSet("hidden", Collections.emptySet())); }

    void setHidden(Set<String> keys) {
        sp.edit().putStringSet("hidden", new HashSet<>(keys)).putLong("stamp", System.nanoTime()).apply();
    }

    void put(String key, Object v) {
        SharedPreferences.Editor e = sp.edit();
        if (v instanceof Boolean) e.putBoolean(key, (Boolean) v);
        else if (v instanceof Float) e.putFloat(key, (Float) v);
        else if (v instanceof Integer) e.putInt(key, (Integer) v);
        else e.putString(key, String.valueOf(v));
        e.putLong("stamp", System.nanoTime()).apply();
    }
}
