package com.praiselab.horizon;

import android.app.Notification;
import android.os.UserManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;
import java.util.Map;

/**
 * Counts notifications per app for badges, keeps each app's latest notification line for live tiles,
 * and gives access to media sessions for the Now Playing card. Everything stays on the phone.
 */
public class BadgeService extends NotificationListenerService {
    @Override public void onListenerConnected() { recount(); Media.start(this); }
    @Override public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) { recount(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap) { recount(); }
    @Override public void onNotificationRankingUpdate(RankingMap rankingMap) { recount(); }
    @Override public void onListenerDisconnected() { Badges.publish(new HashMap<>(), new HashMap<>()); }

    private void recount() {
        StatusBarNotification[] active;
        try { active = getActiveNotifications(); } catch (Exception e) { return; }
        if (active == null) return;
        RankingMap rm = getCurrentRanking();
        Ranking r = new Ranking();
        UserManager um = getSystemService(UserManager.class);
        Map<String, Integer> m = new HashMap<>();
        Map<String, String> pv = new HashMap<>();
        Map<String, Long> when = new HashMap<>();
        for (StatusBarNotification sbn : active) {
            Notification n = sbn.getNotification();
            if (sbn.isOngoing() || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
            if (rm != null && rm.getRanking(sbn.getKey(), r) && !r.canShowBadge()) continue;
            String key = sbn.getPackageName() + "#" + um.getSerialNumberForUser(sbn.getUser());
            m.merge(key, n.number > 0 ? n.number : 1, Integer::sum);
            if (n.visibility == Notification.VISIBILITY_SECRET) continue;
            CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
            if (text == null && title == null) continue;
            Long prev = when.get(key);
            if (prev != null && prev > sbn.getPostTime()) continue;
            when.put(key, sbn.getPostTime());
            pv.put(key, title != null && text != null ? title + ": " + text : String.valueOf(text != null ? text : title));
        }
        Badges.publish(m, pv);
    }
}
