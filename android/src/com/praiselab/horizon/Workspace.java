package com.praiselab.horizon;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

/** Horizontally paged container: Now feed (optional) ← Home tiles → Widgets page. */
final class Workspace extends ViewGroup {
    interface Listener {
        void onPageScroll(float pagePos, float feedProgress);
        void onPageChanged(int page);
    }

    interface Overlay { boolean ready(); void start(); void scroll(float p); void end(); }

    Listener listener;
    /** The Google feed: swiping right on the first page drives it instead of paging. */
    Overlay overlay;
    private boolean overlayDrag;
    View feed;
    int current;
    private final OverScroller scroller;
    private VelocityTracker vt;
    private final int slop, minFling;
    private float downX, downY, lastX;
    private boolean scrolling;

    Workspace(Context c) {
        super(c);
        scroller = new OverScroller(c, new DecelerateInterpolator(2.2f));
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity() * 4;
        setClipChildren(false);
    }

    int firstHomePage() { return feed != null ? 1 : 0; }
    boolean onFeed() { return feed != null && current == 0 && scroller.isFinished() && !scrolling; }
    boolean settled() { return scroller.isFinished() && !scrolling && !spring.running(); }

    @Override
    protected void onMeasure(int ws, int hs) {
        int w = MeasureSpec.getSize(ws), h = MeasureSpec.getSize(hs);
        for (int i = 0; i < getChildCount(); i++)
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t;
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).layout(i * w, 0, (i + 1) * w, h);
        if (scroller.isFinished() && !scrolling && !spring.running() && getScrollX() != current * w) scrollTo(current * w, 0);
    }

    private boolean inStack;

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (spring.running()) { spring.cancel(); scrolling = true; downX = lastX = e.getX(); downY = e.getY(); startTracking(e); return true; }
                downX = lastX = e.getX();
                downY = e.getY();
                inStack = getContext() instanceof Launcher && ((Launcher) getContext()).touchInStack(e.getRawX(), e.getRawY());
                if (!scroller.isFinished()) { scroller.abortAnimation(); scrolling = true; startTracking(e); return true; }
                scrolling = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (!scrolling && !inStack && feed == null && current == 0 && dx > slop && Math.abs(dx) > Math.abs(dy) * 1.3f
                        && overlay != null && overlay.ready()) {
                    overlayDrag = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    overlay.start();
                    return true;
                }
                if (!scrolling && !inStack && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.3f) {
                    scrolling = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    startTracking(e);
                    scrollBy(Math.round(-dx), 0);
                    lastX = e.getX();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!scrolling) break;
                return true;
        }
        return scrolling;
    }

    private void startTracking(MotionEvent e) {
        if (getContext() instanceof Launcher) ((Launcher) getContext()).fast(true);
        if (vt == null) vt = VelocityTracker.obtain();
        vt.clear();
        vt.addMovement(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (overlayDrag) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_MOVE) overlay.scroll((e.getX() - downX) / Math.max(1f, getWidth()));
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { overlay.end(); overlayDrag = false; }
            return true;
        }
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(e);
        int w = getWidth();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!scrolling && Math.abs(e.getX() - downX) > slop && Math.abs(e.getX() - downX) > Math.abs(e.getY() - downY)) {
                    scrolling = true;
                    lastX = e.getX();
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (scrolling) {
                    float d = lastX - e.getX();
                    int max = (getChildCount() - 1) * w;
                    float nx = getScrollX() + d;
                    if (nx < 0 || nx > max) d *= 0.35f; // rubber band at the ends
                    scrollBy(Math.round(d), 0);
                    lastX = e.getX();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (scrolling) {
                    vt.computeCurrentVelocity(1000);
                    float v = vt.getXVelocity();
                    flingV = -v; // scroll position moves opposite to the finger
                    float moved = getScrollX() - current * (float) w;
                    int target = v < -minFling ? (int) Math.floor(getScrollX() / (float) w) + 1
                            : v > minFling ? (int) Math.ceil(getScrollX() / (float) w) - 1
                            : Math.abs(moved) > w * 0.25f ? current + (moved > 0 ? 1 : -1)
                            : Math.round(getScrollX() / (float) Math.max(1, w));
                    snapTo(target);
                }
                scrolling = false;
                return true;
        }
        return true;
    }

    private final Spring spring = new Spring(320f, 0.92f);
    private float flingV;

    void snapTo(int page) {
        page = Math.max(0, Math.min(getChildCount() - 1, page));
        scroller.abortAnimation();
        boolean changed = page != current;
        current = page;
        final int target = page * getWidth();
        if (getContext() instanceof Launcher) ((Launcher) getContext()).fast(true);
        // the page carries on with the swipe's speed and settles like iOS, with a light tick when it lands
        spring.start(getScrollX(), target, flingV, v -> scrollTo(Math.round(v), 0), () -> {
            scrollTo(target, 0);
            if (getContext() instanceof Launcher) ((Launcher) getContext()).fast(false);
            Glass.invalidateAll(); // glass catches up once the page has settled
        });
        flingV = 0;
        if (changed) performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        if (changed && listener != null) listener.onPageChanged(page);
    }

    boolean springing() { return spring.running(); }

    void setCurrentImmediate(int page) {
        current = Math.max(0, Math.min(getChildCount() - 1, page));
        scroller.abortAnimation();
        spring.cancel();
        scrollTo(current * getWidth(), 0);
        if (listener != null) listener.onPageChanged(current);
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), 0);
            postInvalidateOnAnimation();
        } else if (!scrolling && getContext() instanceof Launcher) {
            ((Launcher) getContext()).fast(false);
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int ol, int ot) {
        super.onScrollChanged(l, t, ol, ot);
        if (listener == null || getWidth() == 0) return;
        float pos = l / (float) getWidth();
        listener.onPageScroll(pos, feed != null ? U.clamp(1f - pos, 0f, 1f) : 0f);
    }
}
