package com.praiselab.horizon;

import android.appwidget.AppWidgetProviderInfo;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bottom sheet listing every installed widget, grouped by app, with previews and sizes. */
final class WidgetPicker extends FrameLayout {
    private final Launcher l;
    private final LinearLayout list;
    private final Map<String, List<AppWidgetProviderInfo>> groups = new LinkedHashMap<>();
    private final Map<String, String> groupLabel = new LinkedHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final long target;

    WidgetPicker(Launcher l, String onlyPkg, long target) {
        super(l);
        this.l = l;
        this.target = target;
        U.Pal p = l.pal;
        setBackgroundColor(0x66000000);
        setClickable(true);
        setOnClickListener(v -> l.closeSheet());

        LinearLayout card = new LinearLayout(l);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(p.solid);
        if (p.rim != 0) bg.setStroke(U.dp(1), p.rim);
        float r = U.dp(28);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        card.setBackground(bg);
        card.setClickable(true);
        card.setPadding(0, U.dp(10), 0, l.insetBottom);

        View grab = new View(l);
        grab.setBackground(U.round(p.chip, 3));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(U.dp(40), U.dp(5));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.bottomMargin = U.dp(12);
        card.addView(grab, glp);

        TextView h = U.text(l, 22, p.fg, U.MEDIUM);
        h.setText("Widgets");
        h.setPadding(U.dp(22), 0, U.dp(22), 0);
        card.addView(h);
        TextView sub = U.text(l, 13.5f, p.fg3, U.REGULAR);
        sub.setText(target == Item.PANEL ? "Tap a widget to add it to your Widgets page. Long-press a card there to resize, reorder or remove it."
                : "Tap a widget to add it to Home. Long-press it there to move or resize it.");
        sub.setPadding(U.dp(22), U.dp(6), U.dp(22), U.dp(10));
        card.addView(sub);

        EditText find = new EditText(l);
        find.setHint("Search widgets");
        find.setSingleLine(true);
        find.setTextSize(15);
        find.setTextColor(p.fg);
        find.setHintTextColor(p.fg3);
        find.setBackground(U.round(p.chip, 24));
        find.setPadding(U.dp(18), 0, U.dp(18), 0);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, U.dp(48));
        flp.setMargins(U.dp(16), 0, U.dp(16), U.dp(8));
        card.addView(find, flp);

        ScrollView sv = new ScrollView(l);
        list = new LinearLayout(l);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, U.dp(16));
        sv.addView(list);
        card.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        int height = Math.round(l.root.getHeight() * 0.86f);
        addView(card, new LayoutParams(LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM));
        card.setTranslationY(height);
        card.animate().translationY(0).setDuration(240).start();

        load(onlyPkg);
        render("");
        find.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { render(s.toString().trim().toLowerCase(Locale.ROOT)); }
        });
    }

    private void load(String onlyPkg) {
        List<AppWidgetProviderInfo> all = new ArrayList<>();
        for (UserHandle u : l.apps.um.getUserProfiles()) {
            try { all.addAll(l.awm.getInstalledProvidersForProfile(u)); } catch (Exception ignored) { }
        }
        Map<String, List<AppWidgetProviderInfo>> tmp = new LinkedHashMap<>();
        for (AppWidgetProviderInfo w : all) {
            String pkg = w.provider.getPackageName();
            if (onlyPkg != null && !onlyPkg.equals(pkg)) continue;
            String key = pkg + "#" + l.apps.serial(w.getProfile());
            tmp.computeIfAbsent(key, k -> new ArrayList<>()).add(w);
            if (!groupLabel.containsKey(key)) {
                Apps.App a = l.apps.first(pkg);
                String lab = a != null ? a.label : pkg;
                try { if (a == null) lab = String.valueOf(l.getPackageManager().getApplicationLabel(l.getPackageManager().getApplicationInfo(pkg, 0))); } catch (Exception ignored) { }
                groupLabel.put(key, lab);
            }
        }
        Collator col = Collator.getInstance();
        List<String> keys = new ArrayList<>(tmp.keySet());
        keys.sort((a, b) -> col.compare(groupLabel.get(a), groupLabel.get(b)));
        for (String k : keys) groups.put(k, tmp.get(k));
    }

    private void render(String q) {
        list.removeAllViews();
        U.Pal p = l.pal;
        int shown = 0;
        for (Map.Entry<String, List<AppWidgetProviderInfo>> e : groups.entrySet()) {
            String app = groupLabel.get(e.getKey());
            List<AppWidgetProviderInfo> items = new ArrayList<>();
            for (AppWidgetProviderInfo w : e.getValue()) {
                String lab = w.loadLabel(l.getPackageManager());
                if (q.isEmpty() || app.toLowerCase(Locale.ROOT).contains(q) || (lab != null && lab.toLowerCase(Locale.ROOT).contains(q))) items.add(w);
            }
            if (items.isEmpty()) continue;
            shown++;
            LinearLayout head = new LinearLayout(l);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(U.dp(22), U.dp(14), U.dp(22), U.dp(8));
            ImageView ic = new ImageView(l);
            Apps.App a = l.apps.first(e.getKey().split("#")[0]);
            if (a != null) ic.setImageBitmap(l.apps.icon(a));
            head.addView(ic, new LinearLayout.LayoutParams(U.dp(28), U.dp(28)));
            TextView t = U.text(l, 16, p.fg, U.MEDIUM);
            t.setText(app);
            t.setPadding(U.dp(12), 0, 0, 0);
            head.addView(t);
            list.addView(head);

            HorizontalScrollView hs = new HorizontalScrollView(l);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(l);
            row.setPadding(U.dp(16), 0, U.dp(16), 0);
            hs.addView(row);
            for (AppWidgetProviderInfo w : items) row.addView(tile(w, p));
            list.addView(hs);
        }
        if (shown == 0) {
            TextView none = U.text(l, 15, p.fg2, U.REGULAR);
            none.setText(q.isEmpty() ? "No widgets are installed." : "No widgets match “" + q + "”.");
            none.setPadding(U.dp(22), U.dp(24), U.dp(22), U.dp(24));
            list.addView(none);
        }
    }

    private View tile(AppWidgetProviderInfo w, U.Pal p) {
        LinearLayout t = new LinearLayout(l);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(U.dp(6), U.dp(4), U.dp(6), U.dp(8));
        t.setClickable(true);
        t.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(U.alpha(p.fg, 0.1f)), null, U.round(0xffffffff, 18)));
        ImageView prev = new ImageView(l);
        prev.setScaleType(ImageView.ScaleType.FIT_CENTER);
        prev.setBackground(U.round(p.sunk, 18));
        prev.setPadding(U.dp(8), U.dp(8), U.dp(8), U.dp(8));
        t.addView(prev, new LinearLayout.LayoutParams(U.dp(168), U.dp(112)));
        int dpi = l.getResources().getDisplayMetrics().densityDpi;
        io.execute(() -> {
            Drawable d = null;
            try { d = w.loadPreviewImage(l, dpi); } catch (Exception ignored) { }
            if (d == null) try { d = w.loadIcon(l, dpi); } catch (Exception ignored) { }
            final Drawable fd = d;
            main.post(() -> { if (fd != null) prev.setImageDrawable(fd); });
        });
        TextView name = U.text(l, 14, p.fg, U.MEDIUM);
        name.setText(w.loadLabel(l.getPackageManager()));
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setMaxWidth(U.dp(168));
        name.setPadding(U.dp(2), U.dp(8), 0, 0);
        t.addView(name);
        int[] span = l.spanFor(w);
        TextView size = U.text(l, 12.5f, p.fg3, U.REGULAR);
        size.setText(span[0] + " × " + span[1]);
        size.setPadding(U.dp(2), U.dp(3), 0, 0);
        t.addView(size);
        t.setOnClickListener(v -> l.addWidget(w, target));
        return t;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        io.shutdownNow();
    }
}
