package com.praiselab.horizon;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/**
 * The home tile grid (square cells, grows downward, scrolls vertically) or the dock (one fixed row).
 * A home folder opens inline: its panel is inserted under the folder and the tiles below shift down.
 */
final class CellLayout extends ViewGroup {
    static final class LP extends ViewGroup.LayoutParams {
        int x, y, sx, sy;
        LP(int x, int y, int sx, int sy) { super(MATCH_PARENT, MATCH_PARENT); this.x = x; this.y = y; this.sx = sx; this.sy = sy; }
    }

    final Launcher l;
    final long container;
    int screen, cols = 4, rows = 6, cellW, cellH;
    boolean tiles;
    int minRows = 10;
    /** Tile mode: cell height as a multiple of cell width; spare empty rows kept below the last item. */
    float aspect = 1f;
    int extraRows = 3;
    /** Home: only as many rows as fill the screen (plus spare rows while dragging), so empty space never scrolls. */
    boolean fitScreen, expand, spareWhileDragging;
    /** Home: how many rows fit on screen (0 until measured). */
    int fitRows;
    Runnable onOverflow;
    private int fitFor = -1;
    /** Space between neighbouring items (half on each side of every cell), so all gaps are equal. */
    int gap;
    View panel;
    int panelRow = -1;
    private int panelH;
    private Rect hint;
    private final Paint hf = new Paint(Paint.ANTI_ALIAS_FLAG), hs = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GestureDetector gd;

    CellLayout(Launcher l, long container, int screen) {
        super(l);
        this.l = l;
        this.container = container;
        this.screen = screen;
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        hf.setColor(0x26ffffff);
        hs.setColor(0x99ffffff);
        hs.setStyle(Paint.Style.STROKE);
        hs.setStrokeWidth(U.dp(1.5f));
        gd = new GestureDetector(l, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public void onLongPress(MotionEvent e) {
                int[] c = cellAt(e.getX(), e.getY());
                if (childAt(c[0], c[1]) == null) l.onEmptyLongPress();
            }
            @Override public boolean onDoubleTap(MotionEvent e) { l.onDoubleTap(); return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                // a tap on empty space closes an open folder (but not a tap inside the folder itself)
                if (panel != null && e.getY() >= panel.getTop() && e.getY() <= panel.getBottom()) return false;
                int[] c = cellAt(e.getX(), e.getY());
                if (childAt(c[0], c[1]) == null) l.onEmptyTap();
                return false;
            }
        });
    }

    void setGrid(int cols, int rows) { this.cols = cols; this.rows = rows; requestLayout(); }

    @Override
    protected void onMeasure(int ws, int hs2) {
        int w = MeasureSpec.getSize(ws);
        cellW = Math.max(1, (w - getPaddingLeft() - getPaddingRight()) / cols);
        int h;
        if (tiles) {
            cellH = Math.round(cellW * aspect);
            int maxRow = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (v == panel) continue;
                LP lp = (LP) v.getLayoutParams();
                maxRow = Math.max(maxRow, lp.y + lp.sy);
            }
            rows = Math.max(minRows, maxRow + (spareWhileDragging && !expand ? 0 : extraRows));
            panelH = 0;
            if (panel != null) {
                panel.measure(MeasureSpec.makeMeasureSpec(w - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                panelH = panel.getMeasuredHeight();
            }
            int visible = 0;
            if (fitScreen && getParent() instanceof View) {
                View sc = (View) getParent();
                visible = sc.getHeight() - sc.getPaddingTop() - sc.getPaddingBottom();
                if (visible <= 0) { if (fitFor != 0) { fitFor = 0; post(this::requestLayout); } visible = 0; }
                else {
                    fitFor = visible;
                    int avail = visible - getPaddingTop() - getPaddingBottom() - panelH;
                    int fit = Math.max(1, avail / cellH);
                    // share the leftover height between rows, so the last row sits right above the dock
                    if (panel == null) cellH = Math.max(cellH, avail / fit);
                    if (panel == null) fitRows = fit;
                    // Home never scrolls: nothing may sit below the screen
                    rows = fit;
                    if (maxRow > fit && panel == null && !expand && onOverflow != null) post(onOverflow);
                }
            }
            h = Math.max(visible, getPaddingTop() + rows * cellH + getPaddingBottom() + panelH);
        } else {
            h = MeasureSpec.getSize(hs2);
            cellH = Math.max(1, (h - getPaddingTop() - getPaddingBottom()) / rows);
        }
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v == panel) continue;
            LP lp = (LP) v.getLayoutParams();
            v.measure(MeasureSpec.makeMeasureSpec(Math.max(1, lp.sx * cellW - gap), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.max(1, lp.sy * cellH - gap), MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l0, int t0, int r0, int b0) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v == panel) {
                int top = getPaddingTop() + panelRow * cellH;
                v.layout(getPaddingLeft(), top, getWidth() - getPaddingRight(), top + panelH);
                continue;
            }
            LP lp = (LP) v.getLayoutParams();
            int x = getPaddingLeft() + lp.x * cellW + gap / 2, y = getPaddingTop() + lp.y * cellH + shift(lp.y) + gap / 2;
            v.layout(x, y, x + lp.sx * cellW - gap, y + lp.sy * cellH - gap);
        }
    }

    private int shift(int row) { return panel != null && row >= panelRow ? panelH : 0; }

    /** Opens a panel (an expanded folder) below the given row; the rows from there on move down. */
    void setPanel(View p, int row) {
        clearPanel();
        panel = p;
        panelRow = row;
        addView(p, new LP(0, row, cols, 0));
    }

    void clearPanel() {
        if (panel != null) removeView(panel);
        panel = null;
        panelRow = -1;
        panelH = 0;
    }

    @Override protected boolean checkLayoutParams(ViewGroup.LayoutParams p) { return p instanceof LP; }

    int[] cellAt(float px, float py) {
        if (panel != null) {
            float top = getPaddingTop() + panelRow * cellH;
            if (py >= top + panelH) py -= panelH;
            else if (py >= top) py = top - 1;
        }
        int x = (int) Math.floor((px - getPaddingLeft()) / cellW), y = (int) Math.floor((py - getPaddingTop()) / cellH);
        return new int[]{Math.max(0, Math.min(cols - 1, x)), Math.max(0, Math.min(rows - 1, y))};
    }

    Rect cellRect(int x, int y, int sx, int sy) {
        int l0 = getPaddingLeft() + x * cellW, t0 = getPaddingTop() + y * cellH + shift(y);
        return new Rect(l0, t0, l0 + sx * cellW, t0 + sy * cellH);
    }

    View childAt(int cx, int cy) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v == panel) continue;
            LP lp = (LP) v.getLayoutParams();
            if (cx >= lp.x && cx < lp.x + lp.sx && cy >= lp.y && cy < lp.y + lp.sy) return v;
        }
        return null;
    }

    void setHint(Rect cells) {
        hint = cells;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (hint == null) return;
        Rect r = cellRect(hint.left, hint.top, hint.width(), hint.height());
        RectF f = new RectF(r);
        f.inset(gap / 2f, gap / 2f);
        android.graphics.Path p = Sq.path(f, U.dp(U.R_CARD));
        c.drawPath(p, hf);
        c.drawPath(p, hs);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        gd.onTouchEvent(e);
        return true;
    }
}
