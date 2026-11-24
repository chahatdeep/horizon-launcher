package com.praiselab.horizon;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Root of the home screen. Owns drag and drop, the swipe-up gesture for the app drawer,
 * and transient overlays (long-press menu, widget resize handles).
 */
final class DragLayer extends FrameLayout {
    static final int IDLE = 0, PENDING = 1, DRAGGING = 2;
    static final int FROM_HOME = 0, FROM_DRAWER = 1, FROM_FOLDER = 2;

    /** Where a drag would land right now. */
    static final class Target {
        int bar = -1;
        CellLayout layout;
        int x, y;
        Item merge;
        View mergeView;
        boolean valid;
        int folderSlot = -1; // a new place inside the open folder
    }

    final Launcher l;
    final DropBar bar;
    int state = IDLE;
    Item dragItem;
    View dragSource;
    int dragFrom;
    View popup;
    ResizeFrame resize;

    private final int slop;
    private ImageView dragView;
    private int dragW, dragH;
    private float offX, offY, startX, startY, lastX, lastY;
    private Target hover;
    private int gesture; // 0 undecided, 1 drawer drag, 2 swipe-down done, 3 swallowed, 4 left to a widget
    private float gx, gy;
    private VelocityTracker vt;
    private int scrollDir;
    private final Runnable autoScroll = new Runnable() {
        @Override public void run() {
            if (state == DRAGGING && scrollDir != 0 && l.autoScroll(scrollDir)) {
                updateHover(lastX, lastY);
                postOnAnimation(this);
            }
        }
    };

    DragLayer(Launcher l) {
        super(l);
        this.l = l;
        slop = ViewConfiguration.get(l).getScaledTouchSlop();
        setMotionEventSplittingEnabled(false);
        bar = new DropBar(l);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        lastX = e.getX();
        lastY = e.getY();
        return super.dispatchTouchEvent(e);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (state != IDLE) return true;
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            gesture = 0;
            gx = e.getX();
            gy = e.getY();
            if (popup != null && hit(popup, gx, gy)) return false;
            if (resize != null && resize.handleAt(gx, gy) < 0) { closeResize(); closePopup(); gesture = 3; return true; }
            if (popup != null && !hit(popup, gx, gy)) { closePopup(); gesture = 3; return true; }
            if (l.touchInWidget(gx, gy)) gesture = 4; // scrolling widgets keep vertical swipes
        }
        if (a == MotionEvent.ACTION_MOVE && gesture == 0 && popup == null && resize == null) {
            float dx = e.getX() - gx, dy = e.getY() - gy;
            if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx) * 1.4f) {
                if (dy < 0 && l.canSwipeUp(gy)) {
                    gesture = 1;
                    vt = VelocityTracker.obtain();
                    vt.addMovement(e);
                    l.allApps.prepare();
                    return true;
                }
                if (dy > 0 && l.canSwipeDown()) {
                    gesture = 2;
                    l.onSwipeDown();
                    return false;
                }
                gesture = 4;
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (state != IDLE) { onDrag(e); return true; }
        if (gesture == 1) {
            vt.addMovement(e);
            float p = U.clamp((gy - e.getY()) / (getHeight() * 0.7f), 0f, 1f);
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_MOVE) l.allApps.setProgress(p);
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                vt.computeCurrentVelocity(1000);
                float vy = vt.getYVelocity();
                boolean open = a == MotionEvent.ACTION_UP && (vy < -600 || (vy < 600 && p > 0.25f));
                l.allApps.animateTo(open ? 1f : 0f, -vy / Math.max(1f, getHeight() * 0.7f));
                vt.recycle();
                vt = null;
                gesture = 0;
            }
            return true;
        }
        return gesture == 3;
    }

    // ---------- drag and drop ----------

    void beginDrag(View src, Item item, int from, Bitmap bmp, Rect r) {
        if (state != IDLE || bmp == null) return;
        closeResize();
        state = PENDING;
        dragItem = item;
        dragSource = src;
        dragFrom = from;
        startX = lastX;
        startY = lastY;
        dragW = Math.max(1, r.width());
        dragH = Math.max(1, r.height());
        dragView = new ImageView(getContext());
        dragView.setImageBitmap(bmp);
        dragView.setScaleType(ImageView.ScaleType.FIT_XY);
        addView(dragView, new LayoutParams(dragW, dragH));
        dragView.setX(r.left);
        dragView.setY(r.top);
        offX = lastX - r.left;
        offY = lastY - r.top;
        dragView.animate().scaleX(1.08f).scaleY(1.08f).setDuration(150).start();
        src.setVisibility(INVISIBLE);
        src.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    private void onDrag(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (state == PENDING) {
                    if (Math.hypot(x - startX, y - startY) < slop * 1.5f) return;
                    state = DRAGGING;
                    closePopup();
                    l.onDragStart(dragItem, dragFrom);
                    boolean app = dragItem.type == Item.APP;
                    bar.show(l.pal, dragFrom != FROM_DRAWER, app || dragItem.type == Item.SHORTCUT, app && l.canUninstall(dragItem));
                    bar.bringToFront();
                    dragView.bringToFront();
                }
                dragView.setX(x - offX);
                dragView.setY(y - offY);
                updateHover(x, y);
                break;
            case MotionEvent.ACTION_UP:
                if (state == PENDING) cancelPending(); else drop(false);
                break;
            case MotionEvent.ACTION_CANCEL:
                if (state == PENDING) cancelPending(); else drop(true);
                break;
        }
    }

    private void cancelPending() {
        removeView(dragView);
        dragView = null;
        View src = dragSource;
        Item it = dragItem;
        src.setVisibility(VISIBLE);
        state = IDLE;
        dragItem = null;
        dragSource = null;
        if (popup == null && !it.isIcon()) {
            l.showResize(src);
            if (it.type == Item.STACK || it.type == Item.WIDGET || it.type == Item.GLANCE || it.type == Item.MEDIA || it.container == Item.PANEL) l.showStackMenu(src);
        }
    }

    private void updateHover(float x, float y) {
        Target t = l.findTarget(x, y, dragItem, dragSource, dragView.getX(), dragView.getY());
        clearHover();
        hover = t;
        bar.highlight(t.bar);
        if (t.mergeView != null) Launcher.hover(t.mergeView, true);
        else if (t.layout != null && t.valid) t.layout.setHint(new Rect(t.x, t.y, t.x + dragItem.spanX, t.y + dragItem.spanY));
        int edge = x < U.dp(28) ? -1 : x > getWidth() - U.dp(28) ? 1 : 0;
        if (edge != flipDir) {
            removeCallbacks(flip);
            flipDir = edge;
            if (edge != 0) postDelayed(flip, 450);
        }
        int dir = 0;
        if (t.bar < 0 && l.overPage(x, y)) {
            if (y < l.homeTop() + U.dp(64)) dir = -1;
            else if (y > l.homeBottom() - U.dp(56)) dir = 1;
        }
        if (dir != scrollDir) {
            removeCallbacks(autoScroll);
            scrollDir = dir;
            if (dir != 0) postOnAnimation(autoScroll);
        }
    }

    private void clearHover() {
        if (hover == null) return;
        if (hover.mergeView != null) Launcher.hover(hover.mergeView, false);
        if (hover.layout != null) hover.layout.setHint(null);
    }

    private int flipDir;
    /** Holding a dragged widget at the screen edge flips to the next widget page. */
    private final Runnable flip = new Runnable() {
        @Override public void run() {
            if (state != DRAGGING || flipDir == 0 || dragItem == null) return;
            if (l.dragFlip(dragItem, flipDir)) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            postDelayed(this, 900);
        }
    };

    private void drop(boolean cancelled) {
        removeCallbacks(flip);
        flipDir = 0;
        removeCallbacks(autoScroll);
        scrollDir = 0;
        Target t = cancelled ? null : hover;
        clearHover();
        hover = null;
        bar.hide();
        final ImageView dv = dragView;
        final Item it = dragItem;
        final int w = dragW, h = dragH;
        Rect dest = l.onDrop(it, dragFrom, dragSource, t);
        dragView = null;
        state = IDLE;
        dragItem = null;
        dragSource = null;
        if (dest == null) {
            dv.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(180)
                    .withEndAction(() -> { removeView(dv); l.onDropDone(it); }).start();
            return;
        }
        dv.animate().x(dest.centerX() - w / 2f).y(dest.centerY() - h / 2f)
                .scaleX(dest.width() / (float) w).scaleY(dest.height() / (float) h)
                .setDuration(220).setInterpolator(new DecelerateInterpolator(1.6f))
                .withEndAction(() -> { removeView(dv); l.onDropDone(it); }).start();
    }

    // ---------- overlays ----------

    void showPopup(View p, Rect anchor) {
        closePopup();
        popup = p;
        p.measure(MeasureSpec.makeMeasureSpec(getWidth() - U.dp(24), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST));
        int w = p.getMeasuredWidth(), h = p.getMeasuredHeight();
        int x = Math.max(U.dp(12), Math.min(getWidth() - w - U.dp(12), anchor.centerX() - w / 2));
        int y = anchor.top - h - U.dp(10);
        if (y < l.insetTop + U.dp(8)) y = anchor.bottom + U.dp(10);
        y = Math.max(l.insetTop + U.dp(8), Math.min(getHeight() - h - U.dp(12) - l.insetBottom, y));
        LayoutParams lp = new LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        addView(p, lp);
        p.setAlpha(0f);
        p.setScaleX(0.96f);
        p.setScaleY(0.96f);
        p.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(140).start();
    }

    void closePopup() {
        if (popup != null) { removeView(popup); popup = null; }
    }

    void showResize(ResizeFrame r) {
        closeResize();
        resize = r;
        addView(r, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    void closeResize() {
        if (resize != null) { removeView(resize); resize = null; }
    }

    private static boolean hit(View v, float x, float y) {
        return x >= v.getLeft() && x <= v.getRight() && y >= v.getTop() && y <= v.getBottom();
    }
}
