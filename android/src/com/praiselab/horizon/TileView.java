package com.praiselab.horizon;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * A home tile. Small tiles show just the icon; medium, wide and large tiles add the name and,
 * when there is one, the app's latest notification. Calendar tiles show today's date and next event;
 * folder tiles preview the apps inside.
 */
final class TileView extends View {
    Item item;
    Bitmap icon;
    String label = "";
    boolean showLabel = true;
    /** Label color under small tiles, which sit directly on the wallpaper. */
    private int wallFg = 0xffffffff;
    private boolean wallShadow = true;
    private U.Pal pal;
    private int bg, fg, fg2;
    private int badge;
    private String badgeMode = "count", preview;
    private List<Bitmap> minis = new ArrayList<>();
    private String calDay, calWeek, calEvent;
    private boolean folderHover;
    private float scale = 1f;
    private ValueAnimator anim;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
            bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG), pv = new TextPaint(Paint.ANTI_ALIAS_FLAG),
            big = new TextPaint(Paint.ANTI_ALIAS_FLAG), btp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF(), d = new RectF();

    TileView(Context c) {
        super(c);
        tp.setTypeface(U.REGULAR);
        pv.setTypeface(U.REGULAR);
        big.setTypeface(U.LIGHT);
        btp.setTypeface(U.MEDIUM);
        btp.setColor(0xffffffff);
        btp.setTextAlign(Paint.Align.CENTER);
        btp.setTextSize(sp(11));
        stroke.setStyle(Paint.Style.STROKE);
        setClickable(true);
        setLongClickable(true);
        setFocusable(true);
    }

    private float sp(float v) { return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics()); }

    void bind(Item item, Bitmap icon, String label, U.Pal pal, int tileColor) {
        this.item = item;
        this.icon = icon;
        this.label = label == null ? "" : label;
        this.pal = pal;
        bg = tileColor;
        fg = pal.colorTiles ? 0xffffffff : pal.tileFg;
        fg2 = pal.colorTiles ? 0xd9ffffff : pal.tileFg2;
        setTag(item);
        setContentDescription(this.label);
        invalidate();
    }

    void setMinis(List<Bitmap> m) { minis = m; invalidate(); }

    void setWall(boolean darkText) {
        wallFg = darkText ? 0xff1b1d22 : 0xffffffff;
        wallShadow = !darkText;
        invalidate();
    }
    void setCalendar(String day, String week, String event) { calDay = day; calWeek = week; calEvent = event; invalidate(); }
    void setFolderHover(boolean on) { if (on != folderHover) { folderHover = on; invalidate(); } }

    void setLive(int count, String mode, String text) {
        badge = count;
        badgeMode = mode;
        preview = text;
        invalidate();
    }

    /** Where the icon sits (for launch and drag animations), relative to an ancestor. */
    android.graphics.Rect iconRectIn(View ancestor) { return U.rectIn(this, ancestor); }

    @Override
    protected void onDraw(Canvas c) {
        if (pal == null) return;
        c.save();
        c.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);
        if (small()) {
            drawSmall(c);
            c.restore();
            return;
        }
        tp.clearShadowLayer();
        r.set(0, 0, getWidth(), getHeight());
        float rad = Math.min(U.dp(U.R_CARD), Math.min(r.width(), r.height()) * 0.3f);
        Path shape = Sq.path(r, rad);
        fill.setColor(bg);
        c.drawPath(shape, fill);
        if (pal.rim != 0) {
            stroke.setStrokeWidth(U.dp(1));
            stroke.setColor(pal.rim);
            c.drawPath(shape, stroke);
        }
        if (folderHover) {
            stroke.setStrokeWidth(U.dp(3));
            stroke.setColor(pal.accent);
            c.drawPath(shape, stroke);
        }
        if (item != null && item.type == Item.FOLDER) drawFolder(c);
        else if (calDay != null && !small()) drawCalendar(c);
        else drawApp(c);
        drawBadge(c);
        c.restore();
    }

    /** Small tiles look like a modern app icon: the icon (or a folder preview) with its name below. */
    private void drawSmall(Canvas c) {
        tp.setTextSize(sp(13));
        float labelH = showLabel ? tp.descent() - tp.ascent() + U.dp(6) : 0;
        float s = Math.min(Math.min(getWidth() * 0.78f, U.dp(64)), (getHeight() - labelH) * 0.9f);
        float cx = getWidth() / 2f, top = (getHeight() - s - labelH) / 2f;
        r.set(cx - s / 2, top, cx + s / 2, top + s);
        if (folderHover) {
            fill.setColor(U.alpha(pal.accent, 0.35f));
            c.drawCircle(cx, top + s / 2, s * 0.68f, fill);
        }
        if (item != null && item.type == Item.FOLDER) {
            boolean round = "circle".equals(Apps.shape);
            Path shape = round ? circle(r) : Sq.path(r, s * 0.224f);
            fill.setColor(pal.colorTiles ? bg : wallFg == 0xffffffff ? 0x40ffffff : 0x66ffffff); // frosted glass folder
            c.drawPath(shape, fill);
            if (pal.rim != 0) {
                stroke.setStrokeWidth(U.dp(1));
                stroke.setColor(pal.rim);
                c.drawPath(shape, stroke);
            }
            if (Sq.glassy) glassOver(c, shape, r);
            if (round) {
                // round folder: a 2 × 2 preview that stays inside the circle
                float gap = s * 0.06f, m = s * 0.3f, o = (s - m * 2 - gap) / 2f;
                for (int k = 0; k < Math.min(4, minis.size()); k++)
                    icon(c, minis.get(k), r.left + o + (k % 2) * (m + gap), r.top + o + (k / 2) * (m + gap), m);
            } else {
                // iOS folder: a 3 × 3 preview with equal inner spacing
                float pad = s * 0.13f, gap = s * 0.06f, m = (s - pad * 2 - gap * 2) / 3f;
                for (int k = 0; k < Math.min(9, minis.size()); k++)
                    icon(c, minis.get(k), r.left + pad + (k % 3) * (m + gap), r.top + pad + (k / 3) * (m + gap), m);
            }
        } else {
            icon(c, r.left, r.top, s);
        }
        drawBadge(c);
        if (showLabel) {
            tp.setColor(wallFg);
            if (wallShadow) tp.setShadowLayer(U.dp(3), 0, U.dp(0.5f), 0x73000000); else tp.clearShadowLayer();
            String t = TextUtils.ellipsize(label, tp, getWidth() - U.dp(4), TextUtils.TruncateAt.END).toString();
            c.drawText(t, cx - tp.measureText(t) / 2, r.bottom + U.dp(6) - tp.ascent(), tp);
        }
    }

    private final Path round = new Path();

    private Path circle(android.graphics.RectF b) {
        round.reset();
        round.addCircle(b.centerX(), b.centerY(), b.width() / 2f, Path.Direction.CW);
        return round;
    }

    /** visionOS sheen and light-catching rim over a folder, matching the glossy icons. */
    private void glassOver(Canvas c, Path shape, RectF b) {
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setShader(new android.graphics.LinearGradient(0, b.top, 0, b.top + b.height() * 0.6f, 0x33ffffff, 0x00ffffff, android.graphics.Shader.TileMode.CLAMP));
        c.drawPath(shape, g);
        Paint e = new Paint(Paint.ANTI_ALIAS_FLAG);
        e.setStyle(Paint.Style.STROKE);
        e.setStrokeWidth(U.dp(1));
        e.setShader(new android.graphics.LinearGradient(0, b.top, 0, b.bottom, 0x80ffffff, 0x1affffff, android.graphics.Shader.TileMode.CLAMP));
        c.drawPath(shape, e);
    }

    private boolean small() { return item == null || (item.spanX == 1 && item.spanY == 1); }
    private boolean wide() { return item != null && item.spanX >= 3 && item.spanY <= 2; }

    private void drawApp(Canvas c) {
        if (small()) {
            float s = Math.min(r.width(), r.height()) * 0.64f;
            icon(c, r.centerX() - s / 2, r.centerY() - s / 2, s);
            return;
        }
        float pad = U.dp(12);
        tp.setColor(fg);
        pv.setColor(fg2);
        boolean hasPreview = preview != null && !preview.isEmpty();
        if (wide()) {
            float s = Math.min(r.height() * 0.46f, U.dp(64));
            icon(c, r.left + pad + U.dp(4), r.centerY() - s / 2, s);
            float tx = r.left + pad + U.dp(4) + s + U.dp(14), tw = r.right - pad - tx;
            tp.setTextSize(sp(15));
            if (!hasPreview) {
                text(c, label, tp, tx, r.centerY() - (tp.ascent() + tp.descent()) / 2, tw);
            } else {
                text(c, label, tp, tx, r.top + pad - tp.ascent() + U.dp(4), tw);
                pv.setTextSize(sp(13));
                multi(c, preview, pv, tx, r.top + pad + U.dp(26), tw, 2);
            }
            return;
        }
        boolean large = item.spanX >= 3;
        tp.setTextSize(sp(large ? 15 : 13));
        if (!showLabel && !hasPreview) {
            float s = Math.min(r.width(), r.height()) * (large ? 0.40f : 0.48f);
            icon(c, r.centerX() - s / 2, r.centerY() - s / 2, s);
            return;
        }
        float labelY = r.bottom - pad - tp.descent();
        if (hasPreview) {
            float s = U.dp(large ? 44 : 34);
            icon(c, r.left + pad, r.top + pad, s);
            pv.setTextSize(sp(13));
            float top = r.top + pad + s + U.dp(8);
            int lines = (int) Math.max(1, (labelY - tp.getTextSize() - U.dp(4) - top) / pv.getFontSpacing());
            multi(c, preview, pv, r.left + pad, top, r.width() - pad * 2, Math.min(lines, large ? 6 : 3));
        } else {
            float s = Math.min(r.width(), r.height()) * (large ? 0.36f : 0.42f);
            icon(c, r.centerX() - s / 2, r.top + (r.height() - tp.getTextSize() - pad) / 2 - s / 2, s);
        }
        if (showLabel) text(c, label, tp, r.left + pad, labelY, r.width() - pad * 2 - (badge > 0 ? U.dp(10) : 0));
    }

    private void drawCalendar(Canvas c) {
        float pad = U.dp(12);
        big.setColor(fg);
        tp.setColor(fg);
        pv.setColor(fg2);
        big.setTextSize(sp(wide() ? 40 : 44));
        float dy = r.top + pad - big.ascent() - U.dp(4);
        c.drawText(calDay, r.left + pad, dy, big);
        tp.setTextSize(sp(13));
        float wx = r.left + pad + big.measureText(calDay) + U.dp(8);
        c.drawText(calWeek, wide() ? wx : r.left + pad, wide() ? dy : dy + U.dp(20), tp);
        pv.setTextSize(sp(13));
        String ev = calEvent == null || calEvent.isEmpty() ? "No more events today" : calEvent;
        float top = wide() ? dy + U.dp(10) : dy + U.dp(30);
        int lines = (int) ((r.bottom - pad - top) / pv.getFontSpacing());
        multi(c, ev, pv, r.left + pad, top, r.width() - pad * 2, Math.max(1, Math.min(wide() ? 2 : 3, lines)));
    }

    /** Where each app sits inside a medium or large folder tile, so a tap can open it directly. */
    private final java.util.List<RectF> miniRects = new java.util.ArrayList<>();
    private float touchX = -1, touchY = -1;

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) { touchX = e.getX(); touchY = e.getY(); }
        return super.dispatchTouchEvent(e);
    }

    /** Index of the app under the last tap in a big folder tile, or -1 (label or empty space opens the folder). */
    int miniAt() {
        if (small() || item == null || item.type != Item.FOLDER) return -1;
        for (int k = 0; k < miniRects.size(); k++) if (miniRects.get(k).contains(touchX, touchY)) return k;
        return -1;
    }

    private void drawFolder(Canvas c) {
        float pad = small() ? U.dp(8) : U.dp(12);
        tp.setColor(fg);
        tp.setTextSize(sp(13));
        float bottom = small() || !showLabel ? r.bottom - pad : r.bottom - pad - tp.getTextSize() - U.dp(8);
        boolean few = minis.size() <= 4;
        int cols = small() || (few && !wide()) ? 2 : wide() ? 5 : 3, rows = small() || few || wide() ? 2 : 3;
        float aw = r.width() - pad * 2, ah = bottom - (r.top + pad);
        float cell = Math.min(Math.min(aw / cols, ah / rows), U.dp(64)), gap = cell * 0.16f, s = cell - gap;
        float gx = r.left + pad + (aw - cell * cols) / 2 + gap / 2, gy = r.top + pad + (ah - cell * rows) / 2 + gap / 2;
        miniRects.clear();
        for (int k = 0; k < Math.min(minis.size(), cols * rows); k++) {
            float x = gx + (k % cols) * cell, y = gy + (k / cols) * cell;
            icon(c, minis.get(k), x, y, s);
            miniRects.add(new RectF(x - gap / 2, y - gap / 2, x + s + gap / 2, y + s + gap / 2));
        }
        if (!small() && showLabel) text(c, label, tp, r.left + pad, r.bottom - pad - tp.descent(), r.width() - pad * 2);
    }

    private void drawBadge(Canvas c) {
        if (badge <= 0 || "off".equals(badgeMode)) return;
        fill.setColor(0xffea4335);
        float bx = r.right - U.dp(small() ? 4 : 16), by = r.top + U.dp(small() ? 4 : 16);
        if ("dot".equals(badgeMode)) { c.drawCircle(bx, by, U.dp(5), fill); return; }
        String t = badge > 99 ? "99+" : String.valueOf(badge);
        float h = U.dp(20), w = Math.max(h, btp.measureText(t) + U.dp(10));
        if (!small()) bx -= w / 2 - h / 2;
        c.drawRoundRect(new RectF(bx - w / 2, by - h / 2, bx + w / 2, by + h / 2), h / 2, h / 2, fill);
        c.drawText(t, bx, by - (btp.ascent() + btp.descent()) / 2, btp);
    }

    private void icon(Canvas c, float x, float y, float s) { icon(c, icon, x, y, s); }

    private void icon(Canvas c, Bitmap b, float x, float y, float s) {
        if (b == null) return;
        d.set(x, y, x + s, y + s);
        c.drawBitmap(b, null, d, bmp);
    }

    private static void text(Canvas c, String t, TextPaint p, float x, float y, float w) {
        if (w <= 0) return;
        c.drawText(TextUtils.ellipsize(t, p, w, TextUtils.TruncateAt.END).toString(), x, y, p);
    }

    private static void multi(Canvas c, String t, TextPaint p, float x, float y, float w, int lines) {
        if (w <= 0 || lines <= 0) return;
        StaticLayout sl = StaticLayout.Builder.obtain(t, 0, t.length(), p, (int) w)
                .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0, 1.05f).build();
        c.save();
        c.translate(x, y);
        sl.draw(c);
        c.restore();
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(scale, pressed ? 0.95f : 1f).setDuration(pressed ? 110 : 220);
        anim.addUpdateListener(a -> { scale = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }
}
