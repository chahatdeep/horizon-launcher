package com.praiselab.horizon;

import android.content.pm.ShortcutInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Long-press menu: shortcuts, tile size, then actions for wherever the item lives. */
final class Popup extends LinearLayout {
    private final Launcher l;

    /** Plain action menu (used for Widgets-page cards). Each row: {glyph, label, Runnable, checked}. */
    Popup(Launcher l, Object[][] rows) {
        super(l);
        this.l = l;
        frame();
        for (Object[] r : rows) {
            boolean checked = r.length > 3 && Boolean.TRUE.equals(r[3]);
            action((Integer) r[0], (String) r[1], checked ? l.pal.accent : l.pal.fg, (Runnable) r[2], checked);
        }
    }

    Popup(Launcher l, Item item, int from, View anchor) {
        super(l);
        this.l = l;
        U.Pal p = l.pal;
        frame();

        Apps.App app = item.type == Item.APP ? l.apps.get(item.key()) : null;
        if (app != null) {
            boolean any = false;
            for (ShortcutInfo si : l.apps.shortcuts(app)) {
                CharSequence lab = si.getShortLabel() != null ? si.getShortLabel() : si.getLongLabel();
                View row = row(l.apps.shortcutIcon(si), null, String.valueOf(lab), p.fg);
                row.setOnClickListener(v -> { l.root.closePopup(); l.startShortcut(si, anchor); });
                row.setOnLongClickListener(v -> { l.root.closePopup(); l.pinShortcutToHome(si); return true; });
                any = true;
            }
            if (!any && !l.apps.canShortcuts()) {
                TextView hint = U.text(l, 12.5f, p.fg3, U.REGULAR);
                hint.setText("App shortcuts appear once Horizon is your home app.");
                hint.setPadding(U.dp(12), U.dp(8), U.dp(12), U.dp(8));
                hint.setMaxWidth(U.dp(240));
                addView(hint);
                any = true;
            }
            if (any) divider();
        }

        boolean locked = l.prefs.locked();
        if (!locked && from == DragLayer.FROM_HOME && item.container == Item.DESKTOP && item.isIcon()) {
            sizes(item);
            action(Glyph.RESIZE, "Resize", p.fg, () -> l.showResize(anchor), false);
        }
        if (!locked && from == DragLayer.FROM_DRAWER) action(Glyph.PLUS, "Add to Home", p.fg, () -> l.addToHome(item), false);
        if (app != null && l.hasWidgets(app)) action(Glyph.WIDGETS, "Widgets", p.fg, () -> l.openWidgets(app.pkg, Item.DESKTOP), false);
        if (item.type == Item.APP || item.type == Item.SHORTCUT) action(Glyph.INFO, "App info", p.fg, () -> l.appInfo(item), false);
        if (!locked && from == DragLayer.FROM_HOME)
            action(Glyph.REMOVE, item.type == Item.FOLDER ? "Remove folder" : "Remove from Home", p.fg, () -> l.removeItem(item), false);
        if (from == DragLayer.FROM_FOLDER) action(Glyph.REMOVE, "Move out of folder", p.fg, () -> l.removeFromFolder(item), false);
        if (from == DragLayer.FROM_DRAWER) action(Glyph.HIDE, "Hide from app list", p.fg, () -> l.hideApp(item), false);
        if (app != null && l.canUninstall(item)) action(Glyph.TRASH, "Uninstall", p.danger, () -> l.uninstall(item), false);
    }

    private void frame() {
        U.Pal p = l.pal;
        setOrientation(VERTICAL);
        Sq bg = U.round(p.solid, Math.min(p.radius, 26));
        if (p.rim != 0) bg.setStroke(U.dp(1), p.rim);
        setBackground(bg);
        setElevation(U.dp(10));
        setPadding(U.dp(6), U.dp(6), U.dp(6), U.dp(6));
        setMinimumWidth(U.dp(236));
    }

    /** Small / Medium / Wide / Large, like Windows Phone tiles. */
    private void sizes(Item item) {
        U.Pal p = l.pal;
        TextView h = U.text(l, 12, p.fg3, U.MEDIUM);
        h.setText("TILE SIZE");
        h.setLetterSpacing(0.08f);
        h.setPadding(U.dp(12), U.dp(6), 0, U.dp(6));
        addView(h);
        LinearLayout row = new LinearLayout(l);
        row.setPadding(U.dp(6), 0, U.dp(6), U.dp(6));
        int[][] s = {{1, 1}, {2, 2}, {4, 2}, {4, 4}};
        String[] n = {"S", "M", "W", "L"};
        String[] full = {"Small", "Medium", "Wide", "Large"};
        for (int i = 0; i < 4; i++) {
            final int sx = s[i][0], sy = s[i][1];
            boolean on = item.spanX == sx && item.spanY == sy;
            TextView b = U.text(l, 14, on ? p.onAccent : p.fg, U.MEDIUM);
            b.setText(n[i]);
            b.setGravity(Gravity.CENTER);
            b.setBackground(U.round(on ? p.accent : p.chip, 16));
            b.setContentDescription(full[i] + " tile");
            b.setOnClickListener(v -> { l.root.closePopup(); l.resizeTile(item, sx, sy); });
            LayoutParams lp = new LayoutParams(0, U.dp(38), 1);
            lp.setMargins(U.dp(3), 0, U.dp(3), 0);
            row.addView(b, lp);
        }
        addView(row);
        divider();
    }

    private void divider() {
        View d = new View(l);
        d.setBackgroundColor(l.pal.line);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, U.dp(1));
        lp.setMargins(U.dp(10), U.dp(4), U.dp(10), U.dp(4));
        addView(d, lp);
    }

    private void action(int glyph, String label, int color, Runnable r, boolean checked) {
        View row = row(null, new Glyph(checked ? Glyph.CHECK : glyph, color), label, color);
        row.setOnClickListener(v -> { l.root.closePopup(); r.run(); });
    }

    private View row(Bitmap bmp, Drawable glyph, String label, int color) {
        LinearLayout row = new LinearLayout(l);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(U.dp(10), 0, U.dp(14), 0);
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(U.alpha(l.pal.fg, 0.12f)), null, U.round(0xffffffff, 16)));
        row.setClickable(true);
        ImageView iv = new ImageView(l);
        if (bmp != null) iv.setImageBitmap(bmp); else iv.setImageDrawable(glyph);
        int s = bmp != null ? U.dp(30) : U.dp(22);
        LayoutParams ilp = new LayoutParams(s, s);
        ilp.rightMargin = U.dp(14);
        ilp.leftMargin = bmp != null ? 0 : U.dp(4);
        row.addView(iv, ilp);
        TextView t = U.text(l, 15, color, U.MEDIUM);
        t.setText(label);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(t, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, U.dp(48)));
        return row;
    }
}
