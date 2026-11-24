package com.praiselab.horizon;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** The Widgets page to the right of Home: a 4-column grid of widgets at their natural size, added any time. */
final class WidgetPanel extends ScrollView {
    private View inner;
    private float downY;

    /** A list inside a widget scrolls first; the page scrolls only once that list reaches its end. */
    @Override
    public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
        int a = e.getActionMasked();
        if (a == android.view.MotionEvent.ACTION_DOWN) {
            downY = e.getY();
            inner = scrollerAt(this, e.getRawX(), e.getRawY());
        } else if (a == android.view.MotionEvent.ACTION_MOVE && inner != null) {
            float dy = e.getY() - downY;
            if (inner.canScrollVertically(dy < 0 ? 1 : -1)) return false;
        }
        return super.onInterceptTouchEvent(e);
    }

    private static View scrollerAt(View v, float rx, float ry) {
        if (v.getVisibility() != VISIBLE) return null;
        int[] o = new int[2];
        v.getLocationOnScreen(o);
        if (rx < o[0] || ry < o[1] || rx >= o[0] + v.getWidth() || ry >= o[1] + v.getHeight()) return null;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int k = g.getChildCount() - 1; k >= 0; k--) {
                View f = scrollerAt(g.getChildAt(k), rx, ry);
                if (f != null) return f;
            }
        }
        boolean list = v instanceof android.widget.AbsListView || (v instanceof ScrollView && !(v instanceof WidgetPanel));
        return list ? v : null;
    }

    private String title() {
        String def = page == 0 ? "Widgets" : "Widgets " + (page + 1);
        String n = l.prefs.sp.getString("panelName" + page, "");
        return n.trim().isEmpty() ? def : n;
    }

    private void rename() {
        android.widget.EditText in = new android.widget.EditText(l);
        in.setSingleLine(true);
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        in.setText(title());
        in.selectAll();
        android.widget.FrameLayout box = new android.widget.FrameLayout(l);
        box.setPadding(U.dp(22), U.dp(8), U.dp(22), 0);
        box.addView(in);
        android.app.AlertDialog d = new android.app.AlertDialog.Builder(l, l.pal.dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Rename page")
                .setView(box)
                .setPositiveButton("Save", (dd, w) -> {
                    l.prefs.sp.edit().putString("panelName" + page, in.getText().toString().trim()).apply();
                    rebuild();
                })
                .setNeutralButton("Reset", (dd, w) -> {
                    l.prefs.sp.edit().remove("panelName" + page).apply();
                    rebuild();
                })
                .setNegativeButton("Cancel", null)
                .create();
        d.setOnShowListener(x -> { in.requestFocus(); d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE); });
        d.show();
    }

    private static final String[] SUGGEST = {"com.google.android.apps.tasks", "com.microsoft.todos", "com.spotify.music",
            "com.google.android.calendar", "com.microsoft.office.outlook", "com.google.android.keep", "com.google.android.gm"};
    private final Launcher l;
    private final LinearLayout col;
    final CellLayout grid;
    /** 0-based page number; items on this page have screen == page + 1. */
    final int page;
    private int insetTop, insetBottom;

    WidgetPanel(Launcher l, int page) {
        super(l);
        this.l = l;
        this.page = page;
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        col = new LinearLayout(l);
        col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        grid = new CellLayout(l, Item.PANEL, page + 1);
        grid.tiles = true;
        grid.aspect = 0.82f;
        grid.minRows = 1;
        grid.extraRows = 2;
        grid.spareWhileDragging = true; // no empty row to scroll past; room appears while dragging
        grid.setGrid(4, 2);
        grid.gap = U.dp(10);
    }

    void setInsets(int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
        int side = U.margin() - U.gap() / 2;
        col.setPadding(side, top + U.dp(4), side, bottom + U.dp(20));
    }

    void rebuild() {
        col.removeAllViews();
        U.Pal p = l.pal;
        int hero = l.darkText ? 0xff1b1d22 : 0xffffffff;

        LinearLayout head = new LinearLayout(l);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(U.dp(8), U.dp(2), U.dp(4), U.dp(8));
        TextView t = U.text(l, 24, hero, U.MEDIUM);
        t.setText(title());
        t.setContentDescription("Page name. Tap to rename");
        t.setOnClickListener(v -> rename());
        if (!l.darkText) t.setShadowLayer(U.dp(6), 0, U.dp(1), 0x88000000);
        head.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView add = U.text(l, 15, p.onAccent, U.MEDIUM);
        add.setText("Add");
        add.setGravity(Gravity.CENTER);
        Glyph plus = new Glyph(Glyph.PLUS, p.onAccent);
        plus.setBounds(0, 0, U.dp(18), U.dp(18));
        add.setCompoundDrawables(plus, null, null, null);
        add.setCompoundDrawablePadding(U.dp(6));
        add.setPadding(U.dp(14), 0, U.dp(18), 0);
        add.setBackground(U.round(p.accent, 22));
        add.setContentDescription("Add a widget to this page");
        add.setOnClickListener(v -> l.openWidgets(null, Item.PANEL));
        head.addView(add, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, U.dp(38)));
        boolean empty = l.store.on(Item.PANEL, page + 1).isEmpty();
        if (page > 0 && empty) head.addView(pageButton(Glyph.TRASH, "Remove this page", () -> l.removePanelPage(page)));
        col.addView(head);

        if (page == 0) migrate();
        grid.removeAllViews();
        grid.setGrid(4, 2);
        for (Item it : l.store.on(Item.PANEL, page + 1)) {
            View card = l.createPanelView(it);
            if (card != null) grid.addView(card, new CellLayout.LP(it.x, it.y, it.spanX, it.spanY));
        }
        if (grid.getParent() != null) ((android.view.ViewGroup) grid.getParent()).removeView(grid);
        col.addView(grid, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        if (l.store.on(Item.PANEL, page + 1).isEmpty()) {
            TextView e = U.text(l, 15, p.fg2, U.REGULAR);
            e.setText(page == 0
                    ? "Keep the widgets you check most here: Tasks, Microsoft To Do, Spotify, your calendar. Tap Add, or pick a suggestion below. Long-press a widget to move or resize it."
                    : "An empty widget page. Tap Add to put widgets here, or remove the page with the trash button.");
            e.setLineSpacing(0, 1.2f);
            e.setPadding(U.dp(18), U.dp(18), U.dp(18), U.dp(18));
            Sq bg = U.round(p.surface, Math.min(22, p.radius));
            if (p.rim != 0) bg.setStroke(U.dp(1), p.rim);
            e.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(U.dp(4), 0, U.dp(4), U.dp(12));
            col.addView(e, lp);
        }
        if (page == 0) {
            addRestore(p, hero);
            addSuggestions(p, hero);
        }
        if (page == l.prefs.panelPages() - 1) addNewPage(p);
    }

    /** A clear "New page" button at the end of the last widget page. */
    private void addNewPage(U.Pal p) {
        TextView b = U.text(l, 16, p.fg, U.MEDIUM);
        b.setText("New page");
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        Glyph plus = new Glyph(Glyph.PLUS, p.fg);
        plus.setBounds(0, 0, U.dp(20), U.dp(20));
        b.setCompoundDrawables(plus, null, null, null);
        b.setCompoundDrawablePadding(U.dp(8));
        Sq bg = U.round(p.surface, U.R_CARD);
        bg.setStroke(U.dp(1), U.alpha(p.fg, 0.18f));
        android.widget.FrameLayout btn = new android.widget.FrameLayout(l);
        btn.setBackground(bg);
        btn.setContentDescription("Add a new widget page on the right");
        btn.setOnClickListener(v -> l.addPanelPage());
        btn.addView(b, new android.widget.FrameLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, U.dp(64));
        lp.setMargins(U.dp(4), U.gap(), U.dp(4), U.dp(24));
        col.addView(btn, lp);
    }

    /** Widgets you had on One UI, one tap each to add back (Android asks to allow each one). */
    private void addRestore(U.Pal p, int hero) {
        org.json.JSONArray a;
        try { a = new org.json.JSONArray(l.prefs.sp.getString("pendingWidgets", "[]")); } catch (Exception e) { return; }
        if (a.length() == 0) return;
        TextView s = U.text(l, 13, hero, U.MEDIUM);
        s.setText("FROM ONE UI · TAP TO ADD BACK");
        s.setLetterSpacing(0.08f);
        if (!l.darkText) s.setShadowLayer(U.dp(4), 0, U.dp(1), 0x88000000);
        s.setPadding(U.dp(10), U.dp(6), 0, U.dp(10));
        col.addView(s);
        for (int k = 0; k < a.length(); k++) {
            org.json.JSONObject w = a.optJSONObject(k);
            if (w == null) continue;
            String label = w.optString("label");
            String known = Importer.KNOWN.get(label.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", ""));
            String pkg = known != null && l.apps.first(known) != null ? known : w.optString("pkg");
            Apps.App app = l.apps.first(pkg);
            LinearLayout chip = new LinearLayout(l);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(U.dp(12), 0, U.dp(14), 0);
            Sq bg = U.round(p.surface, 25);
            if (p.rim != 0) bg.setStroke(U.dp(1), p.rim);
            chip.setBackground(bg);
            ImageView ic = new ImageView(l);
            if (app != null) ic.setImageBitmap(l.apps.icon(app)); else ic.setImageDrawable(new Glyph(Glyph.WIDGETS, p.fg2));
            chip.addView(ic, new LinearLayout.LayoutParams(U.dp(30), U.dp(30)));
            TextView tv = U.text(l, 15, p.fg, U.MEDIUM);
            tv.setText(label);
            tv.setPadding(U.dp(12), 0, 0, 0);
            chip.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView add = U.text(l, 14, p.accent, U.MEDIUM);
            add.setText("Add");
            chip.addView(add);
            chip.setContentDescription("Add your " + label + " widget back");
            chip.setOnClickListener(v -> l.openWidgets(pkg, Item.PANEL));
            chip.setOnLongClickListener(v -> { l.clearPendingWidget(pkg); rebuild(); return true; });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, U.dp(54));
            lp.setMargins(U.dp(4), 0, U.dp(4), U.dp(8));
            col.addView(chip, lp);
        }
    }

    private View pageButton(int glyph, String desc, Runnable r) {
        ImageView b = new ImageView(l);
        b.setImageDrawable(new Glyph(glyph, l.pal.fg));
        b.setPadding(U.dp(11), U.dp(11), U.dp(11), U.dp(11));
        b.setBackground(U.round(l.pal.surface, 22));
        b.setContentDescription(desc);
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(U.dp(44), U.dp(44));
        lp.leftMargin = U.dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    /** Cards saved by the old full-width layout get grid positions at their natural size. */
    private void migrate() {
        boolean changed = false;
        List<Item> old = new java.util.ArrayList<>();
        for (Item it : l.store.on(Item.PANEL, 0)) if (it.screen == 0) old.add(it);
        old.sort((a, b) -> Integer.compare(a.x, b.x));
        for (Item it : old) {
            int[] span = l.panelSpanFor(it);
            it.spanX = span[0];
            it.spanY = span[1];
            it.x = -100; // out of the grid until placed
            it.y = -100;
        }
        for (Item it : old) {
            it.screen = 1;
            int[] at = l.store.findFree(Item.PANEL, 1, 4, 60, it.spanX, it.spanY, it);
            it.x = at != null ? at[0] : 0;
            it.y = at != null ? at[1] : 0;
            changed = true;
        }
        if (changed) l.store.save();
    }

    private void addSuggestions(U.Pal p, int hero) {
        LinearLayout block = new LinearLayout(l);
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        int n = 0;
        for (String pkg : SUGGEST) {
            Apps.App a = l.apps.first(pkg);
            if (a == null || !l.hasWidgets(a)) continue;
            if (n % 2 == 0) {
                row = new LinearLayout(l);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, U.dp(50));
                rlp.bottomMargin = U.dp(8);
                block.addView(row, rlp);
            }
            LinearLayout chip = new LinearLayout(l);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(U.dp(10), 0, U.dp(12), 0);
            Sq bg = U.round(p.surface, 25);
            if (p.rim != 0) bg.setStroke(U.dp(1), p.rim);
            chip.setBackground(bg);
            ImageView ic = new ImageView(l);
            ic.setImageBitmap(l.apps.icon(a));
            chip.addView(ic, new LinearLayout.LayoutParams(U.dp(30), U.dp(30)));
            TextView tv = U.text(l, 14, p.fg, U.MEDIUM);
            tv.setText(a.label);
            tv.setSingleLine(true);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setPadding(U.dp(10), 0, 0, 0);
            chip.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            ImageView plus = new ImageView(l);
            plus.setImageDrawable(new Glyph(Glyph.PLUS, p.accent));
            chip.addView(plus, new LinearLayout.LayoutParams(U.dp(20), U.dp(20)));
            chip.setContentDescription("Add a " + a.label + " widget");
            chip.setOnClickListener(v -> l.openWidgets(pkg, Item.PANEL));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
            lp.setMargins(U.dp(4), 0, U.dp(4), 0);
            row.addView(chip, lp);
            n++;
        }
        if (n == 0) return;
        if (n % 2 == 1) row.addView(new View(l), new LinearLayout.LayoutParams(0, 1, 1));
        TextView s = U.text(l, 13, hero, U.MEDIUM);
        s.setText("SUGGESTED");
        s.setLetterSpacing(0.08f);
        if (!l.darkText) s.setShadowLayer(U.dp(4), 0, U.dp(1), 0x88000000);
        s.setPadding(U.dp(10), U.dp(6), 0, U.dp(10));
        col.addView(s);
        col.addView(block);
    }
}
