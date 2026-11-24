package com.praiselab.horizon;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Installed launchable apps (all profiles), their icons and app shortcuts. */
final class Apps {
    static final class App {
        LauncherActivityInfo info;
        String label, key, pkg;
        ComponentName cn;
        UserHandle user;
        long serial;
        int category;
        boolean system, work;
    }

    final Context ctx;
    final LauncherApps la;
    final UserManager um;
    final PackageManager pm;
    final List<App> all = new ArrayList<>();
    final Map<String, App> byKey = new HashMap<>();
    private final Map<String, Bitmap> icons = new HashMap<>();
    int iconPx = 160;
    boolean themed;
    /** circle, squircle or square (rounded), applied to every icon. */
    static String shape = "circle";
    /** Spatial look: icons get a glossy top sheen and a light rim, like visionOS. */
    static boolean glassy;
    /** Tint icons that have no themed version (only when the user picks "Themed, all apps"). */
    static boolean tintAll;

    Apps(Context c) {
        ctx = c;
        la = c.getSystemService(LauncherApps.class);
        um = c.getSystemService(UserManager.class);
        pm = c.getPackageManager();
    }

    void load() {
        all.clear();
        byKey.clear();
        UserHandle me = Process.myUserHandle();
        // app names are slow to read (each app's resources are opened); remember them until the app is updated
        android.content.SharedPreferences names = ctx.getSharedPreferences("labels", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor newNames = null;
        for (UserHandle u : um.getUserProfiles()) {
            long serial = um.getSerialNumberForUser(u);
            List<LauncherActivityInfo> list;
            try { list = la.getActivityList(null, u); } catch (Exception e) { continue; }
            for (LauncherActivityInfo i : list) {
                if (i.getComponentName().getPackageName().equals(ctx.getPackageName())) continue;
                App a = new App();
                a.info = i;
                a.cn = i.getComponentName();
                ApplicationInfo ai0 = i.getApplicationInfo();
                String nk = a.cn.flattenToString() + "#" + serial, stamp = ai0.sourceDir + "|" + java.util.Locale.getDefault();
                String cached = names.getString(nk, null);
                if (cached != null && cached.startsWith(stamp + "\u0001")) a.label = cached.substring(stamp.length() + 1);
                else {
                    a.label = String.valueOf(i.getLabel());
                    if (newNames == null) newNames = names.edit();
                    newNames.putString(nk, stamp + "\u0001" + a.label);
                }
                a.pkg = a.cn.getPackageName();
                a.user = u;
                a.serial = serial;
                a.key = a.cn.flattenToString() + "#" + serial;
                ApplicationInfo ai = i.getApplicationInfo();
                a.category = ai.category;
                a.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                a.work = !u.equals(me);
                all.add(a);
                byKey.put(a.key, a);
            }
        }
        if (newNames != null) newNames.apply();
        Collator col = Collator.getInstance();
        all.sort((x, y) -> col.compare(x.label, y.label));
    }

    App get(String key) { return byKey.get(key); }

    App first(String pkg) {
        if (pkg == null) return null;
        for (App a : all) if (a.pkg.equals(pkg) && !a.work) return a;
        return null;
    }

    App defaultFor(Intent i) {
        ResolveInfo r = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        if (r == null || r.activityInfo == null || "android".equals(r.activityInfo.packageName)) {
            List<ResolveInfo> any = pm.queryIntentActivities(i, 0);
            if (any.isEmpty()) return null;
            r = any.get(0);
        }
        return first(r.activityInfo.packageName);
    }

    long serial(UserHandle u) { return um.getSerialNumberForUser(u); }

    UserHandle user(long serial) {
        UserHandle u = um.getUserForSerialNumber(serial);
        return u != null ? u : Process.myUserHandle();
    }

    /** The app's launcher icon exactly as it ships (activity icon, else the app's), bypassing any system theming. */
    private Drawable ownIcon(App a) {
        try {
            android.content.pm.ApplicationInfo ai = a.info.getApplicationInfo();
            int res = Build.VERSION.SDK_INT >= 31 && a.info.getActivityInfo() != null ? a.info.getActivityInfo().getIconResource() : 0;
            if (res == 0) res = ai.icon;
            if (res == 0) return null;
            android.content.res.Resources r = pm.getResourcesForApplication(ai);
            return r.getDrawableForDensity(res, ctx.getResources().getDisplayMetrics().densityDpi, null);
        } catch (Exception e) { return null; }
    }

    Bitmap icon(App a) {
        Bitmap b = icons.get(a.key);
        if (b != null) return b;
        // finished icons are kept on disk, so a restart doesn't redraw (and re-measure) every app icon
        java.io.File f = new java.io.File(iconDir(), Integer.toHexString(("v7" + a.key + a.info.getApplicationInfo().sourceDir + new java.io.File(a.info.getApplicationInfo().sourceDir).lastModified() + shape + themed + tintAll + glassy + iconPx).hashCode()) + ".png");
        if (f.exists()) {
            b = android.graphics.BitmapFactory.decodeFile(f.getPath());
            if (b != null) { icons.put(a.key, b); return b; }
        }
        Drawable d = null;
        try { d = a.info.getIcon(ctx.getResources().getDisplayMetrics().densityDpi); } catch (Exception ignored) { }
        // Some phones (Samsung) hand launchers a pre-drawn icon without the app's themed (monochrome) layer:
        // for the themed look, read the app's own adaptive icon straight from its resources instead
        int[] tones = null;
        if (themed && d != null && !(d instanceof AdaptiveIconDrawable && ((AdaptiveIconDrawable) d).getMonochrome() != null)) {
            Drawable own = ownIcon(a);
            if (own instanceof AdaptiveIconDrawable && ((AdaptiveIconDrawable) own).getMonochrome() != null) {
                d = own;
            }
        }
        if (d == null) d = pm.getDefaultActivityIcon();
        b = tones != null ? render(ctx, d, iconPx, themed, tones[0], tones[1]) : render(ctx, d, iconPx, themed);
        if (a.work) b = badge(b, a.user);
        icons.put(a.key, b);
        final Bitmap save = b;
        DISK.execute(() -> {
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) { save.compress(Bitmap.CompressFormat.PNG, 100, o); }
            catch (Exception ignored) { }
        });
        return b;
    }

    void clearIcons() { icons.clear(); }

    private static final java.util.concurrent.ExecutorService DISK = java.util.concurrent.Executors.newSingleThreadExecutor();

    private java.io.File iconDir() {
        java.io.File d = new java.io.File(ctx.getCacheDir(), "icons");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private Bitmap badge(Bitmap b, UserHandle u) {
        Drawable d = pm.getUserBadgedIcon(new BitmapDrawable(ctx.getResources(), b), u);
        return U.bitmap(d, b.getWidth(), b.getHeight());
    }

    /**
     * The plate and ink colours of an icon the system already drew in its themed style: the plate is the colour
     * around the edge, the ink the darkest colour inside. Null if it doesn't look like a two-tone themed icon.
     */
    static int[] tones(Drawable d) {
        try {
            int n = 96;
            Bitmap bm = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
            Drawable dd = d.getConstantState() != null ? d.getConstantState().newDrawable().mutate() : d;
            dd.setBounds(0, 0, n, n);
            dd.draw(new Canvas(bm));
            int plate = bm.getPixel(n / 2, n / 8);
            if ((plate >>> 24) < 200) plate = bm.getPixel(n / 8, n / 2);
            if ((plate >>> 24) < 200) return null;
            int ink = plate;
            float best = lum(plate);
            for (int y = n / 5; y < n * 4 / 5; y += 2)
                for (int x = n / 5; x < n * 4 / 5; x += 2) {
                    int c = bm.getPixel(x, y);
                    if ((c >>> 24) > 220 && lum(c) < best) { best = lum(c); ink = c; }
                }
            if (Math.abs(lum(plate) - best) < 0.2f) return null; // one flat colour: not a themed icon
            return new int[]{plate | 0xff000000, ink | 0xff000000};
        } catch (Exception e) { return null; }
    }

    private static float lum(int c) { return (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f; }

    /** Any launcher icon as a full-bleed circle; "mono" uses the wallpaper-derived themed-icon tones. */
    static Bitmap render(Context ctx, Drawable d, int size, boolean mono) { return render(ctx, d, size, mono, 0, 0); }

    static Bitmap render(Context ctx, Drawable d, int size, boolean mono, int tileOver, int inkOver) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Path clip = new Path();
        if ("squircle".equals(shape)) Sq.path(clip, new android.graphics.RectF(0, 0, size, size), size * 0.224f);
        else if ("square".equals(shape)) clip.addRoundRect(0, 0, size, size, size * 0.2f, size * 0.2f, Path.Direction.CW);
        else clip.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW);
        c.clipPath(clip);
        boolean dyn = Build.VERSION.SDK_INT >= 31;
        int tile = tileOver != 0 ? tileOver : dyn ? ctx.getColor(android.R.color.system_accent1_100) : 0xffd3e3fd;
        int ink = inkOver != 0 ? inkOver : dyn ? ctx.getColor(android.R.color.system_accent1_700) : 0xff0b57d0;
        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
            int pad = size / 4; // adaptive layers are 108dp for a 72dp visible area
            Rect r = new Rect(-pad, -pad, size + pad, size + pad);
            Drawable m = Build.VERSION.SDK_INT >= 33 ? a.getMonochrome() : null;
            if (mono && m != null) {
                c.drawColor(tile);
                m = m.mutate();
                m.setBounds(r);
                m.setTint(ink);
                m.draw(c);
            } else if (mono && tintAll) {
                // themed look, but the app ships no themed icon: tint its artwork so every icon matches
                c.drawColor(tile);
                Drawable fg = a.getForeground();
                if (fg != null) { fg = fg.mutate(); fg.setBounds(r); fg.setColorFilter(themeInk(ink)); fg.draw(c); }
            } else {
                Drawable bg = a.getBackground(), fg = a.getForeground();
                if (bg != null) { bg = bg.mutate(); bg.setBounds(r); bg.draw(c); } else c.drawColor(Color.WHITE);
                if (fg != null) { fg = fg.mutate(); fg.setBounds(r); fg.draw(c); }
            }
        } else {
            legacy(c, d, size, clip, mono && tintAll ? tile : Color.WHITE, mono && tintAll ? themeInk(ink) : null);
        }
        if (glassy) {
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
            g.setShader(new android.graphics.LinearGradient(0, 0, 0, size * 0.6f, 0x38ffffff, 0x00ffffff, android.graphics.Shader.TileMode.CLAMP));
            c.drawPath(clip, g);
            Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(Math.max(1f, size * 0.02f));
            rim.setShader(new android.graphics.LinearGradient(0, 0, 0, size, 0x80ffffff, 0x1affffff, android.graphics.Shader.TileMode.CLAMP));
            c.drawPath(clip, rim);
        }
        return b;
    }

    /**
     * Flat icons arrive with their own baked shape (square, rounded, circle, One UI squircle).
     * Ones that fill their shape are cropped and enlarged so Horizon's mask defines the outline;
     * logos on transparency sit on a plate at one consistent size.
     */
    /** Turns any artwork into the themed ink colour: dark and saturated parts solid, light parts fade away. */
    static android.graphics.ColorMatrixColorFilter themeInk(int ink) {
        float r = Color.red(ink), g = Color.green(ink), b = Color.blue(ink);
        return new android.graphics.ColorMatrixColorFilter(new float[]{
                0, 0, 0, 0, r,
                0, 0, 0, 0, g,
                0, 0, 0, 0, b,
                -0.54f, -1.06f, -0.2f, 2f, 0});
    }

    private static void legacy(Canvas c, Drawable d, int size, Path clip, int plate, android.graphics.ColorFilter tint) {
        Bitmap raw = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Drawable dd = d.mutate();
        dd.setBounds(0, 0, size, size);
        dd.draw(new Canvas(raw));
        int[] px = new int[size * size];
        raw.getPixels(px, 0, size, 0, 0, size, size);
        int l = size, t = size, r = -1, btm = -1;
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                if ((px[y * size + x] >>> 24) > 40) {
                    if (x < l) l = x; if (x > r) r = x;
                    if (y < t) t = y; if (y > btm) btm = y;
                }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (r < 0) { p.setColor(plate); c.drawPath(clip, p); return; }
        int bw = r - l + 1, bh = btm - t + 1;
        // a filled shape: opaque at the middle of every edge (a little inside) and nearly square
        int ix = Math.max(1, bw / 12), iy = Math.max(1, bh / 12), cx = l + bw / 2, cy = t + bh / 2;
        boolean filled = Math.abs(bw - bh) < size * 0.08f && bw > size * 0.5f
                && opaque(px, size, l + ix, cy) && opaque(px, size, r - ix, cy)
                && opaque(px, size, cx, t + iy) && opaque(px, size, cx, btm - iy);
        if (tint != null) {
            // themed: the plate in the theme colour, the artwork tinted (whole icon for filled ones, logo otherwise)
            p.setColor(plate);
            c.drawPath(clip, p);
            Paint tinted = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            tinted.setColorFilter(tint);
            float target = filled ? size * 0.78f : size * 0.6f, sc = target / Math.max(bw, bh);
            float w = bw * sc, h = bh * sc;
            c.drawBitmap(raw, new Rect(l, t, r + 1, btm + 1), new RectF((size - w) / 2f, (size - h) / 2f, (size + w) / 2f, (size + h) / 2f), tinted);
            return;
        }
        if (filled) {
            // fill behind with the icon's edge colour so no baked corner shows, then enlarge to cut the old outline
            p.setColor(px[cy * size + l + ix] | 0xff000000);
            c.drawPath(clip, p);
            float grow = 0.09f * size;
            c.drawBitmap(raw, new Rect(l, t, r + 1, btm + 1), new RectF(-grow, -grow, size + grow, size + grow), p);
        } else {
            p.setColor(plate);
            c.drawPath(clip, p);
            float target = size * 0.64f, s = target / Math.max(bw, bh);
            float w = bw * s, h = bh * s;
            c.drawBitmap(raw, new Rect(l, t, r + 1, btm + 1), new RectF((size - w) / 2f, (size - h) / 2f, (size + w) / 2f, (size + h) / 2f), p);
        }
    }

    private static boolean opaque(int[] px, int size, int x, int y) {
        return x >= 0 && y >= 0 && x < size && y < size && (px[y * size + x] >>> 24) > 230;
    }

    // ---------- app shortcuts (only available to the default home app) ----------

    boolean canShortcuts() {
        try { return la.hasShortcutHostPermission(); } catch (Exception e) { return false; }
    }

    List<ShortcutInfo> shortcuts(App a) {
        if (!canShortcuts()) return Collections.emptyList();
        LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery().setPackage(a.pkg).setActivity(a.cn)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST);
        try {
            List<ShortcutInfo> s = la.getShortcuts(q, a.user);
            if (s == null) return Collections.emptyList();
            List<ShortcutInfo> out = new ArrayList<>(s);
            out.sort((x, y) -> {
                int dx = x.isDeclaredInManifest() ? 0 : 1, dy = y.isDeclaredInManifest() ? 0 : 1;
                return dx != dy ? Integer.compare(dx, dy) : Integer.compare(x.getRank(), y.getRank());
            });
            return out.subList(0, Math.min(4, out.size()));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    ShortcutInfo pinned(String pkg, String id, UserHandle u) {
        if (!canShortcuts()) return null;
        LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery().setPackage(pkg)
                .setShortcutIds(Arrays.asList(id))
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED | LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                        | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST);
        try {
            List<ShortcutInfo> s = la.getShortcuts(q, u);
            return s == null || s.isEmpty() ? null : s.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    Bitmap shortcutIcon(ShortcutInfo si) {
        Drawable d = null;
        try { d = la.getShortcutIconDrawable(si, ctx.getResources().getDisplayMetrics().densityDpi); } catch (Exception ignored) { }
        if (d == null) {
            App a = first(si.getPackage());
            return a != null ? icon(a) : render(ctx, pm.getDefaultActivityIcon(), iconPx, false);
        }
        return render(ctx, d, iconPx, false);
    }

    /** Keeps every shortcut placed on the home screen pinned with its app. */
    void syncPins(Store store) {
        if (!canShortcuts()) return;
        Map<String, List<String>> by = new HashMap<>();
        for (Item i : store.items)
            if (i.type == Item.SHORTCUT) by.computeIfAbsent(i.pkg + "#" + i.user, k -> new ArrayList<>()).add(i.shortcutId);
        for (Map.Entry<String, List<String>> e : by.entrySet()) {
            String[] p = e.getKey().split("#");
            try { la.pinShortcuts(p[0], e.getValue(), user(Long.parseLong(p[1]))); } catch (Exception ignored) { }
        }
    }
}
