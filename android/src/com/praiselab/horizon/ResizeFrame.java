package com.praiselab.horizon;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Drag handles around a tile, widget or card. Snaps to whole cells, only grows into free space,
 * and shows the size as you drag.
 */
final class ResizeFrame extends View {
    private static final int LEFT = 0, TOP = 1, RIGHT = 2, BOTTOM = 3;
    final View target;
    private final Launcher l;
    private final CellLayout cl;
    private final Item item;
    private final boolean horiz, vert;
    private final int minX, minY, maxY;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), knob = new Paint(Paint.ANTI_ALIAS_FLAG),
            ring = new Paint(Paint.ANTI_ALIAS_FLAG), label = new Paint(Paint.ANTI_ALIAS_FLAG), pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int handle = -1;
    private float sx, sy;
    private int ox, oy, osx, osy;

    ResizeFrame(Launcher l, View target, Item item, CellLayout cl, boolean horiz, boolean vert, int minX, int minY) {
        super(l);
        this.l = l;
        this.target = target;
        this.item = item;
        this.cl = cl;
        this.horiz = horiz;
        this.vert = vert;
        this.minX = Math.max(1, minX);
        this.minY = Math.max(1, minY);
        this.maxY = 8;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(U.dp(2));
        stroke.setColor(l.pal.accent);
        knob.setColor(l.pal.accent);
        ring.setColor(0xffffffff);
        pill.setColor(0xe6000000);
        label.setColor(0xffffffff);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(U.dp(13));
        label.setTypeface(U.MEDIUM);
    }

    boolean resizable() { return horiz || vert; }

    private RectF box() {
        Rect r = U.rectIn(target, this);
        RectF f = new RectF(r);
        f.inset(U.dp(2), U.dp(2));
        return f;
    }

    private float[][] knobs() {
        RectF b = box();
        return new float[][]{{b.left, b.centerY()}, {b.centerX(), b.top}, {b.right, b.centerY()}, {b.centerX(), b.bottom}};
    }

    private boolean enabled(int h) { return (h == LEFT || h == RIGHT) ? horiz : vert; }

    int handleAt(float x, float y) {
        float[][] k = knobs();
        int best = -1;
        double bd = U.dp(34);
        for (int i = 0; i < 4; i++) {
            if (!enabled(i)) continue;
            double d = Math.hypot(x - k[i][0], y - k[i][1]);
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    @Override
    protected void onDraw(Canvas c) {
        RectF b = box();
        c.drawRoundRect(b, U.dp(18), U.dp(18), stroke);
        float[][] k = knobs();
        for (int i = 0; i < 4; i++) {
            if (!enabled(i)) continue;
            c.drawCircle(k[i][0], k[i][1], U.dp(11), ring);
            c.drawCircle(k[i][0], k[i][1], U.dp(8), knob);
        }
        String t = item.spanX + " × " + item.spanY;
        float w = label.measureText(t) + U.dp(20), h = U.dp(26), cx = b.centerX(), cy = b.top - U.dp(22);
        if (cy - h / 2 < l.insetTop) cy = b.bottom + U.dp(22);
        c.drawRoundRect(new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), h / 2, h / 2, pill);
        c.drawText(t, cx, cy - (label.ascent() + label.descent()) / 2, label);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                handle = handleAt(e.getX(), e.getY());
                if (handle < 0) return false;
                sx = e.getX(); sy = e.getY();
                ox = item.x; oy = item.y; osx = item.spanX; osy = item.spanY;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (handle < 0) return false;
                int dx = Math.round((e.getX() - sx) / cl.cellW), dy = Math.round((e.getY() - sy) / cl.cellH);
                int nx = ox, ny = oy, nsx = osx, nsy = osy;
                if (handle == LEFT) { nx = Math.min(ox + osx - minX, Math.max(0, ox + dx)); nsx = osx + (ox - nx); }
                if (handle == RIGHT) nsx = Math.max(minX, Math.min(cl.cols - ox, osx + dx));
                if (handle == TOP) { ny = Math.min(oy + osy - minY, Math.max(0, oy + dy)); nsy = osy + (oy - ny); }
                if (handle == BOTTOM) nsy = Math.max(minY, Math.min(maxY, osy + dy));
                int rows = Math.max(cl.rows, ny + nsy + 1);
                if ((nx != item.x || ny != item.y || nsx != item.spanX || nsy != item.spanY)
                        && Store.fits(l.store.grid(item.container, item.screen, cl.cols, rows, item), nx, ny, nsx, nsy)) {
                    item.x = nx; item.y = ny; item.spanX = nsx; item.spanY = nsy;
                    CellLayout.LP lp = (CellLayout.LP) target.getLayoutParams();
                    lp.x = nx; lp.y = ny; lp.sx = nsx; lp.sy = nsy;
                    target.requestLayout();
                    target.invalidate();
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    post(this::invalidate);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (handle >= 0) l.store.save();
                handle = -1;
                return true;
        }
        return false;
    }
}
