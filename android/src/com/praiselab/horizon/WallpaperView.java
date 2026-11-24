package com.praiselab.horizon;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.io.File;

/**
 * Horizon's own copy of the wallpaper, drawn slightly larger than the screen so it can move
 * with the phone's tilt while icons and widgets stay still. Shown only when a parallax image is set.
 */
final class WallpaperView extends View {
    /** Extra size around the screen = room to move; set from Parallax strength (0..1). */
    private float zoom = 1.05f;

    void setStrength(float f) { zoom = 1f + 0.14f * Math.max(0f, Math.min(1f, f)); invalidate(); }
    private Bitmap bmp;
    private float tx, ty, page;
    private int dim;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();

    WallpaperView(Context c) {
        super(c);
        setWillNotDraw(false);
    }

    static File file(Context c) { return new File(c.getFilesDir(), "parallax.jpg"); }

    boolean active() { return bmp != null; }

    /** Loads the saved image (or clears it). Returns true if one is shown. */
    boolean load(boolean enabled) {
        bmp = null;
        File f = file(getContext());
        if (enabled && f.exists()) {
            try {
                // never hold more than ~1.3x the screen in memory, whatever size the image was saved at
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getPath(), o);
                int sw = getResources().getDisplayMetrics().widthPixels, sh = getResources().getDisplayMetrics().heightPixels;
                int sample = 1;
                while (o.outWidth / (sample * 2) >= sw * 1.3f && o.outHeight / (sample * 2) >= sh * 1.3f) sample *= 2;
                o.inJustDecodeBounds = false;
                o.inSampleSize = sample;
                bmp = BitmapFactory.decodeFile(f.getPath(), o);
            } catch (Throwable ignored) { bmp = null; }
        }
        setVisibility(bmp != null ? VISIBLE : GONE);
        invalidate();
        return bmp != null;
    }

    void setDim(int percent) { dim = percent; invalidate(); }

    /** tilt in -1..1 on each axis; pagePos 0..1 across all pages. */
    void move(float tiltX, float tiltY, float pagePos) {
        tx = tiltX;
        ty = tiltY;
        page = pagePos;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (bmp == null) return;
        float w = getWidth(), h = getHeight();
        // cover the screen, then enlarge so there is room to move in every direction
        float s = Math.max(w / bmp.getWidth(), h / bmp.getHeight()) * zoom;
        float bw = bmp.getWidth() * s, bh = bmp.getHeight() * s;
        float roomX = (bw - w) / 2f, roomY = (bh - h) / 2f;
        float ox = U.clamp(tx * 0.75f + (page - 0.5f) * 0.5f, -1f, 1f) * roomX;
        float oy = U.clamp(ty, -1f, 1f) * roomY;
        float left = (w - bw) / 2f - ox, top = (h - bh) / 2f - oy;
        dst.set(left, top, left + bw, top + bh);
        c.drawBitmap(bmp, null, dst, paint);
        // glass follows the moving wallpaper, but only once it has moved enough to notice (not on every tiny tilt)
        if (Sq.glassy && Glass.ready() && (Math.abs(dst.left - Glass.dst.left) > U.dp(4) || Math.abs(dst.top - Glass.dst.top) > U.dp(4))) {
            Glass.dst.set(dst);
            post(Glass::invalidateAll);
        }
        if (dim > 0) c.drawColor(U.alpha(0xff000000, dim / 100f));
    }
}
