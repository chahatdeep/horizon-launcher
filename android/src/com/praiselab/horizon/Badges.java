package com.praiselab.horizon;

import android.content.ComponentName;
import android.content.Context;
import android.app.NotificationManager;
import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.Map;

/** Notification counts per app, filled by {@link BadgeService} once the user allows notification access. */
final class Badges {
    interface Listener { void onBadges(); }

    static final Map<String, Integer> counts = new HashMap<>();
    /** Latest notification line per app ("Sender: message"), shown on medium and larger live tiles. */
    static final Map<String, String> previews = new HashMap<>();
    static Listener listener;
    private static final Handler main = new Handler(Looper.getMainLooper());

    private Badges() { }

    static int get(String pkg, long userSerial) {
        Integer n = counts.get(pkg + "#" + userSerial);
        return n == null ? 0 : n;
    }

    static String preview(String pkg, long userSerial) { return previews.get(pkg + "#" + userSerial); }

    static void publish(Map<String, Integer> m, Map<String, String> p) {
        main.post(() -> {
            counts.clear();
            counts.putAll(m);
            previews.clear();
            previews.putAll(p);
            if (listener != null) listener.onBadges();
        });
    }

    static boolean allowed(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationListenerAccessGranted(new ComponentName(c, BadgeService.class));
    }
}
