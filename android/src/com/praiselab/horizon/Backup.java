package com.praiselab.horizon;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Backup and restore of the Home layout and every Horizon setting, as one JSON file.
 * Also keeps a few automatic daily snapshots on the phone so a layout can be rolled back.
 * Widgets come back on the same phone; on a new phone they need adding again (Android ties them to the device).
 */
final class Backup {
    private static final int KEEP = 7;

    static JSONObject snapshot(Context c) throws Exception {
        JSONObject o = new JSONObject().put("app", "Horizon").put("version", 1).put("time", System.currentTimeMillis());
        File ws = new File(c.getFilesDir(), "workspace.json");
        if (ws.exists()) o.put("workspace", new JSONObject(new String(Files.readAllBytes(ws.toPath()), StandardCharsets.UTF_8)));
        JSONObject prefs = new JSONObject();
        for (Map.Entry<String, ?> e : c.getSharedPreferences("launcher", Context.MODE_PRIVATE).getAll().entrySet()) {
            Object v = e.getValue();
            JSONObject t = new JSONObject();
            if (v instanceof Boolean) t.put("b", v);
            else if (v instanceof Integer) t.put("i", v);
            else if (v instanceof Long) t.put("l", v);
            else if (v instanceof Float) t.put("f", (double) (Float) v);
            else if (v instanceof Set) t.put("s", new JSONArray(new ArrayList<>((Set<?>) v)));
            else t.put("t", String.valueOf(v));
            prefs.put(e.getKey(), t);
        }
        o.put("prefs", prefs);
        return o;
    }

    static void write(Context c, Uri to) throws Exception {
        try (OutputStream out = c.getContentResolver().openOutputStream(to, "wt")) {
            out.write(snapshot(c).toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    static void read(Context c, Uri from) throws Exception {
        try (InputStream in = c.getContentResolver().openInputStream(from)) {
            byte[] b = in.readAllBytes();
            apply(c, new JSONObject(new String(b, StandardCharsets.UTF_8)));
        }
    }

    /** Replaces the layout and settings; Home reloads the next time it comes to the front. */
    static void apply(Context c, JSONObject o) throws Exception {
        if (!"Horizon".equals(o.optString("app"))) throw new IllegalArgumentException("Not a Horizon backup");
        snapshotNow(c, "before-restore"); // a restore can always be undone
        JSONObject ws = o.optJSONObject("workspace");
        if (ws != null) Files.write(new File(c.getFilesDir(), "workspace.json").toPath(), ws.toString().getBytes(StandardCharsets.UTF_8));
        JSONObject prefs = o.optJSONObject("prefs");
        if (prefs != null) {
            SharedPreferences.Editor e = c.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit().clear();
            for (java.util.Iterator<String> it = prefs.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONObject t = prefs.getJSONObject(k);
                if (t.has("b")) e.putBoolean(k, t.getBoolean("b"));
                else if (t.has("i")) e.putInt(k, t.getInt("i"));
                else if (t.has("l")) e.putLong(k, t.getLong("l"));
                else if (t.has("f")) e.putFloat(k, (float) t.getDouble("f"));
                else if (t.has("s")) {
                    java.util.HashSet<String> set = new java.util.HashSet<>();
                    JSONArray a = t.getJSONArray("s");
                    for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
                    e.putStringSet(k, set);
                } else e.putString(k, t.optString("t"));
            }
            e.putBoolean("reloadStore", true).putLong("stamp", System.nanoTime()).commit();
        }
    }

    // ---------- automatic snapshots ----------

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "backups");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Once a day (and before a restore), saves a snapshot; keeps the newest few. */
    static void daily(Context c) {
        String day = android.text.format.DateFormat.format("yyyy-MM-dd", System.currentTimeMillis()).toString();
        if (new File(dir(c), "auto-" + day + ".json").exists()) return;
        snapshotNow(c, "auto-" + day);
    }

    static void snapshotNow(Context c, String name) {
        try {
            Files.write(new File(dir(c), name + ".json").toPath(), snapshot(c).toString().getBytes(StandardCharsets.UTF_8));
            File[] all = list(c);
            for (int i = KEEP; i < all.length; i++) all[i].delete();
        } catch (Exception ignored) { }
    }

    /** Snapshots on the phone, newest first. */
    static File[] list(Context c) {
        File[] f = dir(c).listFiles((d, n) -> n.endsWith(".json"));
        if (f == null) return new File[0];
        Arrays.sort(f, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return f;
    }

    static void restoreFile(Context c, File f) throws Exception {
        apply(c, new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
    }

    static List<String> labels(File[] f) {
        List<String> out = new ArrayList<>();
        for (File x : f) {
            String when = android.text.format.DateFormat.format("EEE, MMM d · h:mm a", x.lastModified()).toString();
            out.add((x.getName().startsWith("before-restore") ? "Before last restore · " : "Automatic · ") + when);
        }
        return out;
    }
}
