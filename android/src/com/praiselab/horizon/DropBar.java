package com.praiselab.horizon;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Targets along the top while dragging: Remove, App info, Uninstall. */
final class DropBar extends LinearLayout {
    static final int REMOVE = 0, INFO = 1, UNINSTALL = 2;
    private static final String[] LABELS = {"Remove", "App info", "Uninstall"};
    private static final int[] GLYPHS = {Glyph.REMOVE, Glyph.INFO, Glyph.TRASH};
    private final TextView[] t = new TextView[3];
    private U.Pal pal;
    private int hot = -1;

    DropBar(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        for (int i = 0; i < 3; i++) {
            TextView v = U.text(c, 14, 0, U.MEDIUM);
            v.setText(LABELS[i]);
            v.setGravity(Gravity.CENTER);
            v.setCompoundDrawablePadding(U.dp(8));
            v.setPadding(U.dp(16), 0, U.dp(18), 0);
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, U.dp(46));
            lp.setMargins(U.dp(5), 0, U.dp(5), 0);
            addView(v, lp);
            t[i] = v;
        }
        setVisibility(GONE);
    }

    void show(U.Pal p, boolean remove, boolean info, boolean uninstall) {
        pal = p;
        t[REMOVE].setVisibility(remove ? VISIBLE : GONE);
        t[INFO].setVisibility(info ? VISIBLE : GONE);
        t[UNINSTALL].setVisibility(uninstall ? VISIBLE : GONE);
        hot = -2;
        highlight(-1);
        setAlpha(0f);
        setVisibility(VISIBLE);
        animate().alpha(1f).setDuration(150).start();
    }

    void hide() {
        setVisibility(GONE);
        hot = -1;
    }

    int targetAt(float x, float y) {
        if (getVisibility() != VISIBLE || y > getBottom() + U.dp(16)) return -1;
        for (int i = 0; i < 3; i++) {
            TextView v = t[i];
            if (v.getVisibility() == VISIBLE && x >= v.getLeft() - U.dp(10) && x <= v.getRight() + U.dp(10)) return i;
        }
        return -1;
    }

    void highlight(int i) {
        if (i == hot || pal == null) return;
        hot = i;
        for (int k = 0; k < 3; k++) {
            boolean on = k == i;
            int bg = on ? (k == REMOVE || k == UNINSTALL ? pal.danger : pal.accent) : pal.surface;
            int fg = on ? 0xffffffff : pal.fg;
            t[k].setBackground(U.round(bg, 23));
            t[k].setTextColor(fg);
            Glyph g = new Glyph(GLYPHS[k], fg);
            g.setBounds(0, 0, U.dp(20), U.dp(20));
            t[k].setCompoundDrawables(g, null, null, null);
        }
    }
}
