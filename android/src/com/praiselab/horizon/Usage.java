package com.praiselab.horizon;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** How often (and how recently) each app is opened from Horizon, for Spotlight-style suggestions. Stays on the phone. */
final class Usage {
    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("usage", Context.MODE_PRIVATE); }

    /** True when Android's Usage access is allowed, so recents include apps opened from anywhere. */
    static boolean systemAllowed(Context c) {
        try {
            android.app.AppOpsManager ao = c.getSystemService(android.app.AppOpsManager.class);
            return ao.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.getPackageName())
                    == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) { return false; }
    }

    /** The last n apps used, newest first. */
    private static List<Apps.App> cache;
    private static long cacheAt;

    static List<Apps.App> recent(Context c, Apps apps, java.util.Set<String> hidden, int n) {
        long now0 = System.currentTimeMillis();
        if (cache != null && now0 - cacheAt < 4000) return cache;
        cache = recentNow(c, apps, hidden, n);
        cacheAt = now0;
        return cache;
    }

    private static List<Apps.App> recentNow(Context c, Apps apps, java.util.Set<String> hidden, int n) {
        List<Apps.App> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (systemAllowed(c)) {
            try {
                android.app.usage.UsageStatsManager um = c.getSystemService(android.app.usage.UsageStatsManager.class);
                long now = System.currentTimeMillis();
                List<android.app.usage.UsageStats> st = um.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 7L * 86400000L, now);
                st.sort((a, b) -> Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed()));
                for (android.app.usage.UsageStats u : st) {
                    if (out.size() >= n) break;
                    if (u.getLastTimeUsed() <= 0 || u.getPackageName().equals(c.getPackageName())) continue;
                    Apps.App a = apps.first(u.getPackageName());
                    if (a == null || hidden.contains(a.key) || isLauncher(c, a.pkg) || !seen.add(a.pkg)) continue;
                    out.add(a);
                }
            } catch (Exception ignored) { }
        }
        if (out.size() < n) {
            List<Map.Entry<String, ?>> all = new ArrayList<>(c.getSharedPreferences("recent", Context.MODE_PRIVATE).getAll().entrySet());
            all.sort((a, b) -> Long.compare((Long) b.getValue(), (Long) a.getValue()));
            for (Map.Entry<String, ?> e : all) {
                if (out.size() >= n) break;
                Apps.App a = apps.get(e.getKey());
                if (a == null || hidden.contains(a.key) || !seen.add(a.pkg)) continue;
                out.add(a);
            }
        }
        return out;
    }

    private static java.util.Set<String> launchers;

    /** Home apps are left out of Recent; looked up once, not once per app. */
    private static boolean isLauncher(Context c, String pkg) {
        if (launchers == null) {
            launchers = new java.util.HashSet<>();
            for (android.content.pm.ResolveInfo r : c.getPackageManager().queryIntentActivities(
                    new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME), 0))
                launchers.add(r.activityInfo.packageName);
        }
        return launchers.contains(pkg);
    }

    static void count(Context c, String key) {
        cache = null;
        c.getSharedPreferences("recent", Context.MODE_PRIVATE).edit().putLong(key, System.currentTimeMillis()).apply();
        SharedPreferences s = sp(c);
        s.edit().putFloat(key, s.getFloat(key, 0f) + 1f).apply(); // recency comes from the "recent" timestamps
    }

    /** App keys, most used first. */
    static List<String> top(Context c, int n) {
        List<Map.Entry<String, ?>> all = new ArrayList<>(sp(c).getAll().entrySet());
        all.sort((a, b) -> Float.compare((Float) b.getValue(), (Float) a.getValue()));
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, ?> e : all) { if (out.size() >= n) break; out.add(e.getKey()); }
        return out;
    }
}
