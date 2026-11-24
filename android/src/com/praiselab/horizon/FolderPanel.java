package com.praiselab.horizon;

import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import java.util.List;

/** A home folder opened in place: name, a grid of its apps, and a collapse button. */
final class FolderPanel extends FrameLayout implements FolderView {
    final Item folder;
    private final Launcher l;
    private final EditText title;

    FolderPanel(Launcher l, Item folder) {
        super(l);
        this.l = l;
        this.folder = folder;
        U.Pal p = l.pal;
        setPadding(U.gap() / 2, U.gap() / 2, U.gap() / 2, U.gap() / 2);

        LinearLayout card = new LinearLayout(l);
        card.setOrientation(LinearLayout.VERTICAL);
        Sq bg = U.round(p.surface, U.R_CARD);
        if (p.rim != 0) bg.setStroke(U.dp(1), p.rim);
        card.setBackground(bg);
        card.setPadding(U.dp(12), U.dp(10), U.dp(12), U.dp(14));
        card.setClickable(true);
        addView(card, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout head = new LinearLayout(l);
        head.setGravity(Gravity.CENTER_VERTICAL);
        title = new EditText(l);
        title.setText(folder.title);
        title.setTextSize(18);
        title.setTypeface(U.MEDIUM);
        title.setTextColor(p.fg);
        title.setHint("Folder name");
        title.setHintTextColor(p.fg3);
        title.setSingleLine(true);
        title.setGravity(Gravity.CENTER);
        title.setBackground(null);
        title.setPadding(U.dp(4), U.dp(8), U.dp(4), U.dp(8));
        title.setImeOptions(EditorInfo.IME_ACTION_DONE);
        title.setOnEditorActionListener((v, id, e) -> {
            InputMethodManager imm = l.getSystemService(InputMethodManager.class);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            v.clearFocus();
            saveTitle();
            return true;
        });
        // equal space on both sides keeps the name truly centered over the folder
        head.addView(new View(l), new LinearLayout.LayoutParams(U.dp(40), 1));
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        ImageView close = new ImageView(l);
        close.setImageDrawable(new Glyph(Glyph.UP, p.fg2));
        close.setPadding(U.dp(10), U.dp(10), U.dp(10), U.dp(10));
        close.setBackground(U.round(p.chip, 20));
        close.setContentDescription("Close folder");
        close.setOnClickListener(v -> l.closeInlineFolder());
        head.addView(close, new LinearLayout.LayoutParams(U.dp(40), U.dp(40)));
        card.addView(head);

        this.card = card;
        fill(l.store.children(folder.id), -1);
    }

    private final LinearLayout card;
    private final java.util.List<IconView> cells = new java.util.ArrayList<>();
    private final java.util.List<View> rows = new java.util.ArrayList<>();

    /** The apps in rows of four; the dragged one's spot stays empty. */
    private void fill(List<Item> kids, long hidden) {
        U.Pal p = l.pal;
        for (View r : rows) card.removeView(r);
        rows.clear();
        cells.clear();
        int per = 4, h = l.iconPx + U.dp(42);
        LinearLayout row = null;
        for (int k = 0; k < kids.size(); k++) {
            if (k % per == 0) {
                row = new LinearLayout(l);
                card.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h));
                rows.add(row);
            }
            Item it = kids.get(k);
            IconView iv = new IconView(l, l.iconPx);
            iv.setLabelColor(p.fg, false);
            iv.bind(it, l.iconFor(it), l.labelFor(it));
            iv.setBadge(l.badgeFor(it), l.prefs.badges());
            iv.setOnClickListener(x -> l.onItemClick(x));
            iv.setOnLongClickListener(x -> l.onFolderChildLongClick((IconView) x));
            if (it.id == hidden) iv.setVisibility(INVISIBLE);
            row.addView(iv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
            cells.add(iv);
        }
        if (row != null) for (int k = kids.size() % per; k != 0 && k < per; k++)
            row.addView(new View(l), new LinearLayout.LayoutParams(0, 1, 1));
    }

    @Override public android.graphics.Rect cardRect() { return U.rectIn(card, l.root); }

    @Override public int slotAt(float x, float y) {
        for (int k = 0; k < cells.size(); k++) if (U.rectIn(cells.get(k), l.root).contains(Math.round(x), Math.round(y))) return k;
        return -1;
    }

    @Override public void showOrder(List<Item> order, long hidden) { fill(order, hidden); }

    @Override public android.graphics.Rect slotRect(int n) {
        return n >= 0 && n < cells.size() ? cells.get(n).iconRectIn(l.root) : null;
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
