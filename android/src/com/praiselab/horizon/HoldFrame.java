package com.praiselab.horizon;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/** Detects a long-press over content that handles its own touches (widgets, the at-a-glance header). */
class HoldFrame extends FrameLayout {
    private final int slop;
    private float dx, dy;
    private boolean fired;
    private final Runnable hold = () -> { fired = true; performLongClick(); };

    HoldFrame(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setLongClickable(true);
    }

    private void track(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                fired = false;
                dx = e.getX();
                dy = e.getY();
                removeCallbacks(hold);
                postDelayed(hold, ViewConfiguration.getLongPressTimeout());
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(e.getX() - dx, e.getY() - dy) > slop) removeCallbacks(hold);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(hold);
                break;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        track(e);
        return fired;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        track(e);
        return true;
    }
}
