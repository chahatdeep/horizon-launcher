package com.praiselab.horizon;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * iPhone-style App Library: apps grouped into rounded category cards, two per row.
 * Each card shows three apps you can open directly, and a small cluster that opens the whole category.
 */
final class LibraryView extends ScrollView {
    interface Host {
        void openCategory(String name);
        String categoryOf(Apps.App a);
    }

    private final Launcher l;
    private final Host host;
    private final LinearLayout col;
    private String signature;
    /** Recent apps live in their own strip at the bottom of the drawer, right above the search bar. */
    final LinearLayout recentStrip;
    private int bottomPad;

    LibraryView(Launcher l, Host host) {
        super(l);
        this.l = l;
        this.host = host;
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipToPadding(false);
        col = new LinearLayout(l);
        col.setOrientation(LinearLayout.VERTICAL);
        recentStrip = new LinearLayout(l);
        recentStrip.setOrientation(LinearLayout.VERTICAL);
        recentStrip.setPadding(U.dp(6), U.dp(6), U.dp(6), 0);
        addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    void setBottomInset(int b) { bottomPad = b; col.setPadding(U.dp(32), U.dp(2), U.dp(32), b + U.dp(16)); }

    void invalidateContent() { signature = null; }

    private Set<String> pending;

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w > 0 && pending != null) { Set<String> p = pending; pending = null; post(() -> update(p)); }
    }

    /** Rebuilds only when the app list (or look) changed. */
    void update(Set<String> hidden) {
        if (getWidth() == 0) { pending = hidden; return; } // not laid out yet (drawer closed): build when it gets a size
        Map<String, List<Apps.App>> groups = new LinkedHashMap<>();
        List<Apps.App> recent = new ArrayList<>();
        StringBuilder sig = new StringBuilder().append(getWidth()).append(l.pal.style).append(l.pal.dark);
        for (Apps.App a : l.apps.all) {
            if (hidden.contains(a.key)) continue;
            sig.append(a.key.hashCode()).append(',');
            recent.add(a);
        }
        List<Apps.App> sugg = Usage.recent(l, l.apps, hidden, 10);
        for (Apps.App a : sugg) sig.append(a.key);
        boolean sys = Usage.systemAllowed(l);
        sig.append(sys);
        if (sig.toString().equals(signature)) return;
        signature = sig.toString();

        recent.sort((x, y) -> Long.compare(installed(y), installed(x)));
        if (recent.size() > 8) recent = new ArrayList<>(recent.subList(0, 8));
        groups.put("Recently Added", recent);
        Map<String, List<Apps.App>> byCat = new LinkedHashMap<>();
        for (String c : AppCategories.ORDER) byCat.put(c, new ArrayList<>());
        for (Apps.App a : l.apps.all) if (!hidden.contains(a.key)) byCat.get(host.categoryOf(a)).add(a);
        for (Map.Entry<String, List<Apps.App>> e : byCat.entrySet()) if (!e.getValue().isEmpty()) groups.put(e.getKey(), e.getValue());

        col.removeAllViews();
        recentStrip.removeAllViews();
        LinearLayout col = recentStrip; // the Recent strip below is built into the bottom strip
        // Recent: one clean row of five icons, no names, sized to the tray it sits in
        if (false) { // the Usage access option lives in Settings > App drawer instead
            TextView allow = U.text(l, 12.5f, l.pal.accent, U.MEDIUM);
            allow.setText("Include apps opened outside Horizon");
            allow.setPadding(U.dp(10), U.dp(4), U.dp(10), U.dp(2));
            allow.setOnClickListener(v -> l.startSafely(new android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .setData(android.net.Uri.parse("package:" + l.getPackageName()))));
            col.addView(allow);
        }
        if (!sugg.isEmpty()) {
            int inner = getWidth() - U.dp(2 * (10 + 8 + 6)); // drawer margins + tray padding + strip padding
            int per = 5, each = inner / per, icon = Math.min(U.dp(56), Math.round(each * 0.66f));
            LinearLayout sr = null;
            for (int k = 0; k < Math.min(10, sugg.size()); k++) {
                if (k % per == 0) {
                    sr = new LinearLayout(l);
                    col.addView(sr, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                }
                IconView v = (IconView) big(sugg.get(k), icon);
                v.showLabel = false;
                sr.addView(v, new LinearLayout.LayoutParams(each, icon + U.dp(14)));
            }
        }
        col = this.col;
        int gap = U.dp(22);
        int cardW = (getWidth() - col.getPaddingLeft() - col.getPaddingRight() - gap) / 2;
        LinearLayout row = null;
        int n = 0;
        for (Map.Entry<String, List<Apps.App>> e : groups.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            if (n % 2 == 0) {
                row = new LinearLayout(l);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                rl.bottomMargin = U.dp(14);
                col.addView(row, rl);
            }
            View card = card(e.getKey(), e.getValue(), cardW);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(cardW, LayoutParams.WRAP_CONTENT);
            if (n % 2 == 1) cl.leftMargin = gap;
            row.addView(card, cl);
            n++;
        }
    }

    private long installed(Apps.App a) {
        try { return l.getPackageManager().getPackageInfo(a.pkg, 0).firstInstallTime; } catch (Exception e) { return 0; }
    }

    private View card(String name, List<Apps.App> apps, int w) {
        U.Pal p = l.pal;
        LinearLayout outer = new LinearLayout(l);
        outer.setOrientation(LinearLayout.VERTICAL);

        int pad = Math.round(w * 0.07f), inner = w - pad * 2, g = Math.round(w * 0.06f), cell = (inner - g) / 2;
        FrameLayout box = new FrameLayout(l);
        Sq bg = U.round(p.dark ? U.alpha(0xffffffff, 0.09f) : U.alpha(0xff000000, 0.06f), 26);
        box.setBackground(bg);
        box.setPadding(pad, pad, pad, pad);
        boolean isRecent = name.equals("Recently Added");
        for (int k = 0; k < 4 && k < apps.size(); k++) {
            int x = (k % 2) * (cell + g), y = (k / 2) * (cell + g);
            View v;
            int inset = Math.round(cell * 0.1f);
            if (k == 3 && apps.size() > 4) v = cluster(apps.subList(3, Math.min(7, apps.size())), cell - inset * 2, name);
            else v = big(apps.get(k), cell - inset * 2);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cell - inset * 2, cell - inset * 2);
            lp.leftMargin = x + inset;
            lp.topMargin = y + inset;
            box.addView(v, lp);
        }
        box.setMinimumHeight(w);
        if (!isRecent) box.setOnClickListener(v -> host.openCategory(name));
        else box.setOnClickListener(v -> host.openCategory(null));
        outer.addView(box, new LinearLayout.LayoutParams(w, w));

        TextView t = U.text(l, 14, p.fg, U.MEDIUM);
        t.setText(name);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tl.topMargin = U.dp(5);
        outer.addView(t, tl);
        return outer;
    }

    private View big(Apps.App a, int size) {
        IconView v = new IconView(l, size);
        v.showLabel = false;
        Item it = Item.app(a);
        it.container = Item.DRAWER;
        v.bind(it, l.apps.icon(a), a.label);
        v.setBadge(Badges.get(a.pkg, a.serial), l.prefs.badges());
        v.setContentDescription(a.label);
        v.setOnClickListener(x -> l.launchApp(a, x));
        v.setOnLongClickListener(x -> l.onDrawerLongClick((IconView) x));
        return v;
    }

    /** The bottom-right corner: four small icons that open the whole category. */
    private View cluster(List<Apps.App> apps, int size, String name) {
        FrameLayout f = new FrameLayout(l);
        int g = Math.round(size * 0.1f), s = (size - g) / 2;
        for (int k = 0; k < apps.size() && k < 4; k++) {
            ImageView iv = new ImageView(l);
            iv.setImageBitmap(l.apps.icon(apps.get(k)));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(s, s);
            lp.leftMargin = (k % 2) * (s + g);
            lp.topMargin = (k / 2) * (s + g);
            f.addView(iv, lp);
        }
        f.setContentDescription("All " + name + " apps");
        f.setOnClickListener(v -> host.openCategory(name.equals("Recently Added") ? null : name));
        return f;
    }
}
