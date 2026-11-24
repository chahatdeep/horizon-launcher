package com.praiselab.horizon;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

/** Hosts one app widget on the home screen and keeps it told about its real size. */
final class WidgetFrame extends HoldFrame {
    final Item item;
    AppWidgetHostView hv;
    AppWidgetProviderInfo info;
    private int lastW, lastH;

    WidgetFrame(Launcher l, Item item) {
        super(l);
        this.item = item;
        setTag(item);
        setWillNotDraw(false);
    }

    private final android.graphics.Path mask = new android.graphics.Path();
    private final android.graphics.RectF box = new android.graphics.RectF();

    /** Clips every widget to the same rounded corners, whatever the app draws. */
    @Override
    protected void dispatchDraw(android.graphics.Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) { super.dispatchDraw(c); return; }
        box.set(0, 0, w, h);
        if (hv != null) box.set(hv.getLeft() + hv.getPaddingLeft(), hv.getTop() + hv.getPaddingTop(),
                hv.getRight() - hv.getPaddingRight(), hv.getBottom() - hv.getPaddingBottom());
        mask.reset();
        Sq.path(mask, box, U.dp(U.R_CARD));
        int sc = c.save();
        c.clipPath(mask);
        super.dispatchDraw(c);
        c.restoreToCount(sc);
    }

    void setWidget(AppWidgetHostView v, AppWidgetProviderInfo info) {
        this.hv = v;
        this.info = info;
        v.setPadding(0, 0, 0, 0); // Android insets older widgets; Horizon's grid already spaces them
        addView(v, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    void setPlaceholder(String text, U.Pal p) {
        TextView t = U.text(getContext(), 14, p.fg2, U.REGULAR);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setPadding(U.dp(12), U.dp(12), U.dp(12), U.dp(12));
        t.setBackground(U.round(p.surface, U.R_CARD));
        addView(t, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (hv != null && w > 0 && h > 0 && (w != lastW || h != lastH)) {
            lastW = w;
            lastH = h;
            post(this::updateSize);
        }
    }

    private void updateSize() {
        float d = U.density; // Horizon's dp: widgets draw with Horizon's content size, so report sizes in the same units
        int wd = Math.round((getWidth() - getPaddingLeft() - getPaddingRight()) / d);
        int hd = Math.round((getHeight() - getPaddingTop() - getPaddingBottom()) / d);
        Bundle o = new Bundle();
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wd);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wd);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, hd);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, hd);
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                ArrayList<SizeF> sizes = new ArrayList<>();
                sizes.add(new SizeF(wd, hd));
                hv.updateAppWidgetSize(o, sizes);
            } else {
                hv.updateAppWidgetSize(o, wd, hd, wd, hd);
            }
        } catch (Exception ignored) { }
    }

    /** Widgets with their own scrolling lists keep vertical swipes instead of opening the app drawer. */
    boolean scrollable() { return hasScroller(this); }

    private static boolean hasScroller(View v) {
        if (v instanceof AbsListView || v instanceof ScrollView) return true;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) if (hasScroller(g.getChildAt(i))) return true;
        }
        return false;
    }
}
