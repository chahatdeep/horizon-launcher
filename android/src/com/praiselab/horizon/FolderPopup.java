package com.praiselab.horizon;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;

import java.util.List;

/** An open folder: editable name above a grid of its apps. Tap outside to close. */
final class FolderPopup extends FrameLayout implements FolderView {
    final Item folder;
    private final Launcher l;
    private final EditText title;
    private final List<Item> kids;
    private LinearLayout card;
    private GridView grid;
    private BaseAdapter adapter;
    private long hidden = -1;

    FolderPopup(Launcher l, Item folder, android.graphics.Rect anchor) {
        super(l);
        this.l = l;
        this.folder = folder;
        this.kids = new java.util.ArrayList<>(l.store.children(folder.id));
        U.Pal p = l.pal;
        setBackgroundColor(0x59000000);
        setClickable(true);
        setOnClickListener(v -> l.closeFolder());

        int n = Math.max(1, kids.size());
        int cols = n <= 4 ? Math.max(3, n) : 4;
        int rows = (int) Math.ceil(n / (double) cols);
        int screenW = l.getResources().getDisplayMetrics().widthPixels;
        int cardW = screenW - U.dp(24) * 2;
        int icon = Math.round(l.iconPx * 1.12f);
        int cellW = (cardW - U.dp(16) * 2) / cols, cellH = icon + U.dp(40);

        card = new LinearLayout(l);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(U.round(p.surface, 30));
        card.setPadding(U.dp(16), U.dp(14), U.dp(16), U.dp(14));
        card.setElevation(U.dp(10));
        card.setClickable(true);

        title = new EditText(l);
        title.setText(folder.title);
        title.setTextSize(22);
        title.setTypeface(U.MEDIUM);
        title.setTextColor(p.fg);
        title.setHint("Folder name");
        title.setHintTextColor(p.fg3);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setBackground(null);
        title.setImeOptions(EditorInfo.IME_ACTION_DONE);
        title.setOnEditorActionListener((v, id, e) -> {
            InputMethodManager imm = l.getSystemService(InputMethodManager.class);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            v.clearFocus();
            saveTitle();
            return true;
        });
        card.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        grid = new GridView(l);
        grid.setNumColumns(cols);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        grid.setVerticalScrollBarEnabled(false);
        grid.setAdapter(adapter = new BaseAdapter() {
            @Override public int getCount() { return kids.size(); }
            @Override public Object getItem(int i) { return kids.get(i); }
            @Override public long getItemId(int i) { return kids.get(i).id; }
            @Override public View getView(int i, View convert, ViewGroup parent) {
                IconView v = new IconView(l, icon);
                v.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, cellH));
                Item it = kids.get(i);
                v.setLabelColor(p.fg, false);
                v.bind(it, l.iconFor(it), l.labelFor(it));
                v.setBadge(l.badgeFor(it), l.prefs.badges());
                v.setOnClickListener(x -> l.onItemClick(x));
                v.setOnLongClickListener(x -> l.onFolderChildLongClick((IconView) x));
                if (it.id == hidden) v.setVisibility(INVISIBLE);
                return v;
            }
        });
        int visibleRows = Math.min(rows, 4);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(cols * cellW, visibleRows * cellH);
        glp.topMargin = U.dp(6);
        card.addView(grid, glp);

        LayoutParams clp = new LayoutParams(cardW, LayoutParams.WRAP_CONTENT, anchor != null ? Gravity.TOP | Gravity.CENTER_HORIZONTAL : Gravity.CENTER);
        addView(card, clp);
        card.setAlpha(0f);
        card.setScaleX(0.85f);
        card.setScaleY(0.85f);
        // open right next to the folder: below it when there is room, otherwise above, and grow out of it
        card.post(() -> {
            if (anchor != null) {
                int h = card.getHeight(), H = getHeight(), gap = U.dp(10);
                int top = anchor.bottom + gap;
                if (top + h > H - l.insetBottom - U.dp(16)) top = anchor.top - h - gap;
                top = Math.max(l.insetTop + U.dp(8), Math.min(H - l.insetBottom - h - U.dp(8), top));
                card.setY(top);
                card.setPivotX(anchor.centerX() - card.getX());
                card.setPivotY(anchor.centerY() - top);
            }
            card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
        });
    }

    @Override public android.graphics.Rect cardRect() { return U.rectIn(card, l.root); }

    @Override public int slotAt(float x, float y) {
        android.graphics.Rect g = U.rectIn(grid, l.root);
        int pos = grid.pointToPosition(Math.round(x - g.left), Math.round(y - g.top));
        return pos == GridView.INVALID_POSITION ? -1 : pos;
    }

    @Override public void showOrder(List<Item> order, long hide) {
        kids.clear();
        kids.addAll(order);
        hidden = hide;
        adapter.notifyDataSetChanged();
    }

    @Override public android.graphics.Rect slotRect(int n) {
        View c = grid.getChildAt(n - grid.getFirstVisiblePosition());
        return c instanceof IconView ? ((IconView) c).iconRectIn(l.root) : c != null ? U.rectIn(c, l.root) : null;
    }

    void saveTitle() {
        String t = title.getText().toString().trim();
        if (t.isEmpty()) t = "Folder";
        if (!t.equals(folder.title)) {
            folder.title = t;
            l.store.save();
            l.refreshFolderIcon(folder);
        }
    }
}
