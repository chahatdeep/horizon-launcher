package com.praiselab.horizon;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Apple-style "Liquid Glass" for the Spatial look: each glass surface shows the wallpaper behind it,
 * heavily blurred, with a faint tint and bright edges that catch the light top-left and bottom-right.
 * Needs a copy of the wallpaper (Settings > Wallpaper for glass and parallax); without one it falls back to tinted glass.
 */
final class Glass {
    /** Blurred, downscaled wallpaper, and where the full wallpaper sits on screen (screen pixels). */
    static Bitmap blurred;
    static final RectF dst = new RectF();
    private static final List<WeakReference<View>> views = new ArrayList<>();
    private static final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    /** Liquid Glass blur is optional (Settings > Look); off by default because it redraws on every frame of motion. */
    static boolean enabled;

    static boolean ready() { return enabled && blurred != null && dst.width() > 0; }

    /** Loads (or clears) the wallpaper copy and blurs it once. */
    static void load(Context c, int screenW, int screenH) {
        blurred = null;
        try {
            java.io.File f = WallpaperView.file(c);
            if (!f.exists()) return;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 8;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
            if (b == null) return;
            int w = 160, h = Math.max(1, Math.round(b.getHeight() * 160f / b.getWidth()));
            Bitmap small = Bitmap.createScaledBitmap(b, w, h, true).copy(Bitmap.Config.ARGB_8888, true);
            blur(small, 6);
            blur(small, 6);
            blur(small, 6);
            blurred = small;
            // where the wallpaper is drawn when Horizon is not moving it: centre-cropped to the screen
            float s = Math.max(screenW / (float) b.getWidth(), screenH / (float) b.getHeight());
            float bw = b.getWidth() * s, bh = b.getHeight() * s;
            dst.set((screenW - bw) / 2f, (screenH - bh) / 2f, (screenW + bw) / 2f, (screenH + bh) / 2f);
        } catch (Throwable ignored) { blurred = null; }
    }

    /** Keeps glass in step when the wallpaper moves (parallax) or pages scroll. */
    static void moved(RectF wallpaperOnScreen) {
        if (wallpaperOnScreen != null) dst.set(wallpaperOnScreen);
        invalidateAll();
    }

    static void track(View v) {
        if (v == null) return;
        for (WeakReference<View> r : views) if (r.get() == v) return;
        views.add(new WeakReference<>(v));
    }

    static void invalidateAll() {
        if (!Sq.glassy || !ready()) return; // nothing to keep in step unless Liquid Glass is showing
        for (int i = views.size() - 1; i >= 0; i--) {
            View v = views.get(i).get();
            if (v == null) views.remove(i); else v.invalidate();
        }
    }

    private static final int[] loc = new int[2];

    /** Draws the glass body (blurred wallpaper + tint) inside the path. b is the drawable's box in view coordinates. */
    static void body(Canvas c, Path path, RectF b, View host, int tint) {
        if (ready() && host != null && host.isAttachedToWindow()) {
            host.getLocationOnScreen(loc);
            int save = c.save();
            c.clipPath(path);
            float sx = dst.width() / blurred.getWidth(), sy = dst.height() / blurred.getHeight();
            // view (0,0) is at loc on screen; the wallpaper's top-left is at dst.left/top on screen
            RectF r = new RectF(dst.left - loc[0], dst.top - loc[1], dst.left - loc[0] + blurred.getWidth() * sx, dst.top - loc[1] + blurred.getHeight() * sy);
            c.drawBitmap(blurred, null, r, bmp);
            c.restoreToCount(save);
        }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(tint);
        c.drawPath(path, p);
    }

    /** The light-catching rim: bright at the top-left and bottom-right, fading through the middle, plus a soft inner glow. */
    static void rim(Canvas c, Path path, RectF b) {
        Paint e = new Paint(Paint.ANTI_ALIAS_FLAG);
        e.setStyle(Paint.Style.STROKE);
        e.setStrokeWidth(U.dp(1.4f));
        e.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                new int[]{0x8cffffff, 0x1affffff, 0x08ffffff, 0x1affffff, 0x59ffffff},
                new float[]{0f, 0.25f, 0.5f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(path, e);
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        float h = Math.min(b.height(), U.dp(70));
        glow.setShader(new LinearGradient(0, b.top, 0, b.top + h, 0x14ffffff, 0x00ffffff, Shader.TileMode.CLAMP));
        c.drawPath(path, glow);
    }

    /** Small box blur, run a few times for a smooth, Gaussian-like result. */
    private static void blur(Bitmap b, int r) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h], out = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 2; pass++) {
            boolean horiz = pass == 0;
            int n = horiz ? h : w, len = horiz ? w : h;
            for (int line = 0; line < n; line++) {
                int ra = 0, ga = 0, ba = 0;
                for (int k = -r; k <= r; k++) {
                    int c = px[idx(horiz, line, Math.max(0, Math.min(len - 1, k)), w)];
                    ra += (c >> 16) & 255; ga += (c >> 8) & 255; ba += c & 255;
                }
                int d = r * 2 + 1;
                for (int i = 0; i < len; i++) {
                    out[idx(horiz, line, i, w)] = 0xff000000 | ((ra / d) << 16) | ((ga / d) << 8) | (ba / d);
                    int add = px[idx(horiz, line, Math.min(len - 1, i + r + 1), w)];
                    int sub = px[idx(horiz, line, Math.max(0, i - r), w)];
                    ra += ((add >> 16) & 255) - ((sub >> 16) & 255);
                    ga += ((add >> 8) & 255) - ((sub >> 8) & 255);
                    ba += (add & 255) - (sub & 255);
                }
            }
            int[] t = px; px = out; out = t;
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static int idx(boolean horiz, int line, int i, int w) { return horiz ? line * w + i : i * w + line; }
}
