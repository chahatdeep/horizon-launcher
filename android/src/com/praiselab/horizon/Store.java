package com.praiselab.horizon;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** The home screen layout, persisted as JSON in app-private storage. */
final class Store {
    final File file;
    final List<Item> items = new ArrayList<>();
    long nextId = 1;

    Store(Context c) {
        file = new File(c.getFilesDir(), "workspace.json");
        load();
    }

    void load() {
        items.clear();
        if (!file.exists()) return;
        try {
            JSONObject o = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            nextId = o.optLong("nextId", 1);
            JSONArray a = o.getJSONArray("items");
            for (int i = 0; i < a.length(); i++) items.add(Item.from(a.getJSONObject(i)));
        } catch (Exception ignored) { }
    }

    private static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newSingleThreadExecutor();

    /** Snapshots the layout on the main thread (fast), writes it in the background (in order, never torn). */
    void save() {
        final byte[] data;
        try {
            JSONArray a = new JSONArray();
            for (Item i : items) a.put(i.json());
            data = new JSONObject().put("version", 1).put("nextId", nextId).put("items", a)
                    .toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) { return; }
        IO.execute(() -> {
            try {
                File tmp = new File(file.getPath() + ".tmp");
                Files.write(tmp.toPath(), data);
                if (!tmp.renameTo(file)) Files.write(file.toPath(), data);
            } catch (Exception ignored) { }
        });
    }

    Item add(Item i) {
        i.id = nextId++;
        items.add(i);
        return i;
    }

    Item byId(long id) {
        for (Item i : items) if (i.id == id) return i;
        return null;
    }

    void remove(Item i) {
        items.remove(i);
        if (i.type == Item.FOLDER || i.type == Item.STACK) items.removeIf(c -> c.container == i.id);
    }

    List<Item> on(long container, int screen) {
        List<Item> out = new ArrayList<>();
        for (Item i : items)
            if (i.container == container && ((container != Item.DESKTOP && container != Item.PANEL) || i.screen == screen)) out.add(i);
        return out;
    }

    List<Item> children(long folder) {
        List<Item> out = new ArrayList<>();
        for (Item i : items) if (i.container == folder) out.add(i);
        out.sort((a, b) -> Integer.compare(a.x, b.x));
        return out;
    }

    int screens() {
        int m = 1;
        for (Item i : items) if (i.container == Item.DESKTOP) m = Math.max(m, i.screen + 1);
        return m;
    }

    boolean[][] grid(long container, int screen, int cols, int rows, Item ignore) {
        boolean[][] g = new boolean[cols][rows];
        for (Item i : on(container, screen)) {
            if (i == ignore || (ignore != null && i.id == ignore.id && ignore.id != 0)) continue;
            for (int x = i.x; x < i.x + i.spanX && x < cols; x++)
                for (int y = i.y; y < i.y + i.spanY && y < rows; y++)
                    if (x >= 0 && y >= 0) g[x][y] = true;
        }
        return g;
    }

    static boolean fits(boolean[][] g, int x, int y, int sx, int sy) {
        if (x < 0 || y < 0 || x + sx > g.length || g.length == 0 || y + sy > g[0].length) return false;
        for (int i = x; i < x + sx; i++) for (int j = y; j < y + sy; j++) if (g[i][j]) return false;
        return true;
    }

    int[] findFree(long container, int screen, int cols, int rows, int sx, int sy, Item ignore) {
        boolean[][] g = grid(container, screen, cols, rows, ignore);
        for (int y = 0; y + sy <= rows; y++)
            for (int x = 0; x + sx <= cols; x++)
                if (fits(g, x, y, sx, sy)) return new int[]{x, y};
        return null;
    }

    int[] nearestFree(long container, int screen, int cols, int rows, int sx, int sy, Item ignore, int tx, int ty) {
        boolean[][] g = grid(container, screen, cols, rows, ignore);
        int[] best = null;
        double bd = Double.MAX_VALUE;
        for (int y = 0; y + sy <= rows; y++)
            for (int x = 0; x + sx <= cols; x++) {
                if (!fits(g, x, y, sx, sy)) continue;
                double d = Math.hypot(x - tx, y - ty);
                if (d < bd) { bd = d; best = new int[]{x, y}; }
            }
        return best;
    }

    /** Drops empty home pages in the middle or at the end; returns true when anything moved. */
    boolean compact() {
        int n = screens(), out = 0;
        boolean changed = false;
        for (int s = 0; s < n; s++) {
            boolean empty = true;
            for (Item i : items) if (i.container == Item.DESKTOP && i.screen == s) { empty = false; break; }
            if (empty && s > 0) { changed = true; continue; }
            if (out != s) for (Item i : items) if (i.container == Item.DESKTOP && i.screen == s) i.screen = out;
            out++;
        }
        return changed;
    }
}
