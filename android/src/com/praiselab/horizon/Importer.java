package com.praiselab.horizon;

import android.appwidget.AppWidgetProviderInfo;
import android.os.UserHandle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rebuilds Home from another launcher's layout (read from One UI over USB and dropped into
 * Horizon's own folder as import.json). Apps are matched by name on this phone.
 *
 * Format: {"source", "dock": [label], "pages": [[{"type":"app","label"} | {"type":"folder","title","apps":[label]}]],
 *          "widgets": [label], "settings": {"badges"}}
 */
final class Importer {
    private final Launcher l;
    private final Map<String, Apps.App> byLabel = new HashMap<>();
    int apps, folders, missing;
    final List<String> notFound = new ArrayList<>();

    Importer(Launcher l) {
        this.l = l;
        for (Apps.App a : l.apps.all) {
            String k = norm(a.label);
            Apps.App prev = byLabel.get(k);
            if (prev == null || (prev.work && !a.work)) byLabel.put(k, a);
        }
    }

    /** One UI names for common widgets whose labels are ambiguous (YouTube also has a "Search" widget). */
    static final Map<String, String> KNOWN = new HashMap<>();
    static {
        KNOWN.put("search", "com.google.android.googlequicksearchbox");
        KNOWN.put("ataglance", "com.google.android.googlequicksearchbox");
        KNOWN.put("google", "com.google.android.googlequicksearchbox");
        KNOWN.put("digital", "com.sec.android.app.clockpackage");
        KNOWN.put("sleep", "com.sec.android.app.shealth");
        KNOWN.put("dailyactivity", "com.sec.android.app.shealth");
        KNOWN.put("todo", "com.microsoft.todos");
    }

    static File file(Launcher l) { return new File(l.getExternalFilesDir(null), "import.json"); }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private Apps.App find(String label) {
        String k = norm(label);
        Apps.App a = byLabel.get(k);
        if (a != null || k.isEmpty()) return a;
        for (Map.Entry<String, Apps.App> e : byLabel.entrySet())
            if (e.getKey().startsWith(k) || k.startsWith(e.getKey())) return e.getValue();
        return null;
    }

    /** Replaces Home and the dock with the imported layout. The Widgets page is kept. */
    void apply(JSONObject o) throws Exception {
        Store store = l.store;
        for (Item i : new ArrayList<>(store.items)) {
            if (i.container == Item.PANEL) continue;
            if (i.type == Item.WIDGET && i.widgetId >= 0) { try { l.host.deleteAppWidgetId(i.widgetId); } catch (Exception ignored) { } }
            store.items.remove(i);
        }
        int cols = l.cols;
        if (l.prefs.glance()) {
            Item g = new Item();
            g.type = Item.GLANCE;
            g.spanX = cols;
            g.spanY = 2;
            store.add(g);
        }

        JSONArray dock = o.optJSONArray("dock");
        if (dock != null && dock.length() > 0) l.prefs.sp.edit().putInt("dockCount", Math.max(4, Math.min(5, dock.length()))).apply();
        int dx = 0;
        if (dock != null) for (int k = 0; k < dock.length() && dx < l.prefs.dockCount(); k++) {
            JSONObject fo = dock.optJSONObject(k);
            if (fo != null) {
                if (dockFolder(fo, dx)) dx++;
                continue;
            }
            Apps.App a = find(dock.optString(k));
            if (a == null) { miss(dock.optString(k)); continue; }
            Item it = Item.app(a);
            it.container = Item.HOTSEAT;
            it.x = dx++;
            store.add(it);
            apps++;
        }

        Item media = new Item();
        media.type = Item.MEDIA;
        media.spanX = Math.min(4, cols);
        media.spanY = 2;
        put(media);

        JSONArray pages = o.optJSONArray("pages");
        if (pages != null) for (int p = 0; p < pages.length(); p++) {
            JSONArray page = pages.optJSONArray(p);
            if (page == null) continue;
            for (int k = 0; k < page.length(); k++) {
                JSONObject e = page.optJSONObject(k);
                if (e == null) continue;
                if ("folder".equals(e.optString("type"))) addFolder(e);
                else {
                    Apps.App a = find(e.optString("label"));
                    if (a == null) { miss(e.optString("label")); continue; }
                    Item it = Item.app(a);
                    it.spanX = it.spanY = 1;
                    put(it);
                    apps++;
                }
            }
        }

        JSONArray widgets = o.optJSONArray("widgets");
        JSONArray pending = new JSONArray();
        if (widgets != null) for (int k = 0; k < widgets.length(); k++) {
            String pkg = widgetPackage(widgets.optString(k));
            if (pkg != null) pending.put(new JSONObject().put("label", widgets.optString(k)).put("pkg", pkg));
        }
        l.prefs.sp.edit().putString("pendingWidgets", pending.toString()).apply();

        JSONObject s = o.optJSONObject("settings");
        if (s != null && s.has("badges")) l.prefs.put("badges", s.optString("badges"));
        store.save();
    }

    private boolean dockFolder(JSONObject e, int x) {
        JSONArray list = e.optJSONArray("apps");
        List<Apps.App> found = new ArrayList<>();
        if (list != null) for (int k = 0; k < list.length(); k++) {
            Apps.App a = find(list.optString(k));
            if (a == null) miss(list.optString(k)); else found.add(a);
        }
        if (found.isEmpty()) return false;
        Item f;
        if (found.size() == 1) {
            f = Item.app(found.get(0));
            f.container = Item.HOTSEAT;
            f.x = x;
            l.store.add(f);
            apps++;
            return true;
        }
        f = new Item();
        f.type = Item.FOLDER;
        f.title = e.optString("title", "Folder");
        f.container = Item.HOTSEAT;
        f.x = x;
        l.store.add(f);
        int rank = 0;
        for (Apps.App a : found) {
            Item it = Item.app(a);
            it.container = f.id;
            it.x = rank++;
            l.store.add(it);
            apps++;
        }
        folders++;
        return true;
    }

    private void addFolder(JSONObject e) {
        JSONArray list = e.optJSONArray("apps");
        List<Apps.App> found = new ArrayList<>();
        if (list != null) for (int k = 0; k < list.length(); k++) {
            Apps.App a = find(list.optString(k));
            if (a == null) miss(list.optString(k)); else found.add(a);
        }
        if (found.isEmpty()) return;
        if (found.size() == 1) {
            Item it = Item.app(found.get(0));
            put(it);
            apps++;
            return;
        }
        Item f = new Item();
        f.type = Item.FOLDER;
        String t = e.optString("title").trim();
        f.title = t.isEmpty() ? "Folder" : t;
        f.spanX = f.spanY = 1;
        put(f);
        int rank = 0;
        for (Apps.App a : found) {
            Item it = Item.app(a);
            it.container = f.id;
            it.x = rank++;
            l.store.add(it);
            apps++;
        }
        folders++;
    }

    private void put(Item it) {
        Store s = l.store;
        int rows = 4;
        for (Item i : s.items) if (i.container == Item.DESKTOP) rows = Math.max(rows, i.y + i.spanY);
        int[] at = s.findFree(Item.DESKTOP, 0, l.cols, rows + it.spanY + 1, it.spanX, it.spanY, it);
        it.container = Item.DESKTOP;
        it.x = at != null ? at[0] : 0;
        it.y = at != null ? at[1] : rows;
        s.add(it);
    }

    private void miss(String label) {
        missing++;
        if (label != null && !label.isEmpty() && notFound.size() < 8) notFound.add(label);
    }

    /** Matches a One UI widget name (e.g. "ChatGPT", "Sleep") to the app that provides it. */
    private String widgetPackage(String label) {
        String k = norm(label);
        if (k.isEmpty()) return null;
        String known = KNOWN.get(k);
        if (known != null && l.apps.first(known) != null) return known;
        for (UserHandle u : l.apps.um.getUserProfiles()) {
            List<AppWidgetProviderInfo> list;
            try { list = l.awm.getInstalledProvidersForProfile(u); } catch (Exception e) { continue; }
            for (AppWidgetProviderInfo w : list) {
                String pkg = w.provider.getPackageName();
                Apps.App a = l.apps.first(pkg);
                if (norm(w.loadLabel(l.getPackageManager())).equals(k) || (a != null && norm(a.label).equals(k))) return pkg;
            }
        }
        Apps.App a = find(label);
        return a != null && l.hasWidgets(a) ? a.pkg : null;
    }

    static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }
}
