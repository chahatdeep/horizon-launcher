package com.praiselab.horizon;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A widget stack: several widgets share one card, and a sideways swipe switches between them.
 * Dots at the bottom show which one is in front.
 */
final class StackView extends HoldFrame {
    private static final Map<Long, Integer> shown = new HashMap<>();
    final Item item;
    final List<View> cards = new ArrayList<>();
    private int cur;
    private final int slop;
    private float downX, downY, offset;
    private boolean swiping;
    private VelocityTracker vt;
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG), pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pr = new RectF();
    /** The page dots only show while swiping (and briefly after), so the card stays clean. */
    private float dots;
    private final Runnable hideDots = () -> animateDots(0f);

    private void animateDots(float to) {
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(dots, to);
        a.setDuration(220);
        a.addUpdateListener(an -> { dots = (float) an.getAnimatedValue(); invalidate(); });
        a.start();
    }

    StackView(Launcher l, Item item, List<View> kids) {
        super(l);
        this.item = item;
        slop = ViewConfiguration.get(l).getScaledTouchSlop();
        for (View k : kids) {
            k.setLongClickable(false);
            cards.add(k);
            addView(k, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        Integer s = shown.get(item.id);
        if (s == null) s = l.prefs.sp.getInt("stackShown" + item.id, 0); // remember the front card across restarts
        cur = s != null && s < cards.size() ? s : 0;
        pill.setColor(0x59000000);
        setWillNotDraw(false);
        layoutCards();
    }

    /** The widget in front (its Item). */
    static void show(long stackId, int index) { shown.put(stackId, index); }
    static void remember(Launcher l, long stackId, int index) { show(stackId, index); l.prefs.sp.edit().putInt("stackShown" + stackId, index).apply(); }

    Item current() { return cards.isEmpty() ? null : (Item) cards.get(cur).getTag(); }

    int count() { return cards.size(); }

    boolean scrollable() {
        View c = cards.isEmpty() ? null : cards.get(cur);
        return c instanceof WidgetFrame && ((WidgetFrame) c).scrollable();
    }

    private int wrap(int i) { int n = cards.size(); return ((i % n) + n) % n; }

    private void layoutCards() {
        int w = Math.max(1, getWidth());
        for (int k = 0; k < cards.size(); k++) {
            View c = cards.get(k);
            float tx;
            if (k == cur) tx = offset;
            else if (cards.size() > 1 && k == wrap(cur + 1) && offset < 0) tx = offset + w;
            else if (cards.size() > 1 && k == wrap(cur - 1) && offset > 0) tx = offset - w;
            else { c.setVisibility(INVISIBLE); continue; }
            c.setVisibility(VISIBLE);
            c.setTranslationX(tx);
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        layoutCards();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        boolean held = super.onInterceptTouchEvent(e);
        if (held) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                swiping = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (cards.size() > 1 && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.3f) {
                    startSwipe(e);
                    return true;
                }
                break;
        }
        return false;
    }

    private void startSwipe(MotionEvent e) {
        swiping = true;
        removeCallbacks(hideDots);
        animateDots(1f);
        getParent().requestDisallowInterceptTouchEvent(true);
        if (vt == null) vt = VelocityTracker.obtain();
        vt.clear();
        vt.addMovement(e);
        downX = e.getX();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        super.onTouchEvent(e);
        if (!swiping) {
            if (e.getActionMasked() == MotionEvent.ACTION_MOVE && cards.size() > 1) {
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.3f) startSwipe(e);
            }
            return true;
        }
        vt.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                offset = e.getX() - downX;
                layoutCards();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                vt.computeCurrentVelocity(1000);
                float v = vt.getXVelocity(), w = getWidth();
                int dir = 0;
                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (offset < -w * 0.25f || v < -800) dir = 1;
                    else if (offset > w * 0.25f || v > 800) dir = -1;
                }
                settle(dir);
                swiping = false;
                break;
        }
        return true;
    }

    private void settle(int dir) {
        float from = offset, to = dir == 0 ? 0 : -dir * getWidth();
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(from, to);
        a.setDuration(220);
        a.setInterpolator(new DecelerateInterpolator(1.6f));
        a.addUpdateListener(an -> { offset = (float) an.getAnimatedValue(); layoutCards(); });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator an) {
                if (dir != 0) {
                    cur = wrap(cur + dir);
                    shown.put(item.id, cur);
                    ((Launcher) getContext()).prefs.sp.edit().putInt("stackShown" + item.id, cur).apply();
                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                }
                offset = 0;
                layoutCards();
                removeCallbacks(hideDots);
                postDelayed(hideDots, 1200);
            }
        });
        a.start();
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        int n = cards.size();
        if (n < 2 || dots <= 0.01f) return;
        pill.setAlpha(Math.round(0x59 * dots));
        float r = U.dp(3), gap = U.dp(7), w = (n - 1) * gap + n * r * 2, cx = getWidth() / 2f, cy = getHeight() - U.dp(9);
        pr.set(cx - w / 2 - U.dp(6), cy - r - U.dp(4), cx + w / 2 + U.dp(6), cy + r + U.dp(4));
        c.drawRoundRect(pr, pr.height() / 2, pr.height() / 2, pill);
        float x = cx - w / 2 + r;
        for (int k = 0; k < n; k++) {
            dot.setColor(k == cur ? 0xffffffff : 0x80ffffff);
            dot.setAlpha(Math.round((k == cur ? 255 : 128) * dots));
            c.drawCircle(x, cy, r, dot);
            x += r * 2 + gap;
        }
    }
}
