package com.praiselab.horizon;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/** Tracks the most relevant media session (Spotify or any player) for the Now Playing card. */
final class Media {
    interface Listener { void onMedia(); }

    static final List<Listener> listeners = new ArrayList<>();
    static MediaController current;
    static boolean allowed;
    private static MediaSessionManager msm;
    private static final Handler main = new Handler(Looper.getMainLooper());

    private static final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(android.media.session.PlaybackState s) { changed(); }
        @Override public void onMetadataChanged(android.media.MediaMetadata m) { changed(); }
        @Override public void onSessionDestroyed() { current = null; changed(); }
    };
    private static final MediaSessionManager.OnActiveSessionsChangedListener sessions = Media::pick;

    private Media() { }

    static void start(Context c) {
        Context app = c.getApplicationContext();
        if (msm == null) msm = app.getSystemService(MediaSessionManager.class);
        ComponentName cn = new ComponentName(app, BadgeService.class);
        try {
            msm.removeOnActiveSessionsChangedListener(sessions);
            msm.addOnActiveSessionsChangedListener(sessions, cn, main);
            allowed = true;
            pick(msm.getActiveSessions(cn));
        } catch (SecurityException e) {
            allowed = false;
            changed();
        }
    }

    private static void pick(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController m : list) {
                PlaybackState s = m.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { best = m; break; }
                if (best == null) best = m;
            }
        }
        boolean same = current != null && best != null && current.getSessionToken().equals(best.getSessionToken());
        if (!same) {
            if (current != null) current.unregisterCallback(callback);
            if (best != null) best.registerCallback(callback, main);
            current = best;
        }
        changed();
    }

    /**
     * Skips a track. Some players ignore the skip command in certain modes, so if the song hasn't changed half a
     * second later the headset media key is sent; if there is simply nothing queued after it, the player's own
     * "radio" for this song keeps the music going.
     */
    static void skip(Launcher l, android.view.View v, boolean forward) {
        v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        MediaController m = current;
        String before = title(m);
        if (m != null) { if (forward) m.getTransportControls().skipToNext(); else m.getTransportControls().skipToPrevious(); }
        main.postDelayed(() -> {
            MediaController now = current;
            if (m != null && before != null && !before.equals(title(now))) return;
            int key = forward ? android.view.KeyEvent.KEYCODE_MEDIA_NEXT : android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS;
            if (now != null) {
                now.dispatchMediaButtonEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key));
                now.dispatchMediaButtonEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key));
                if (forward) main.postDelayed(() -> radioIfStuck(l, before), 900);
            }
        }, 500);
    }

    private static String title(MediaController m) {
        return m != null && m.getMetadata() != null ? String.valueOf(m.getMetadata().getDescription().getTitle()) : null;
    }

    private static void radioIfStuck(Launcher l, String before) {
        MediaController m = current;
        if (m == null || m.getPlaybackState() == null || before == null || !before.equals(title(m))) return;
        for (PlaybackState.CustomAction a : m.getPlaybackState().getCustomActions()) {
            if (String.valueOf(a.getName()).toLowerCase(java.util.Locale.ROOT).contains("radio")) {
                m.getTransportControls().sendCustomAction(a, null);
                U.toast(l, "No next song queued, so playing radio based on this one");
                return;
            }
        }
    }

    static boolean playing() {
        PlaybackState s = current != null ? current.getPlaybackState() : null;
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    /** Redraw every Now Playing card (after its settings change). */
    static void refresh() { changed(); }

    private static void changed() {
        for (Listener l : new ArrayList<>(listeners)) l.onMedia();
    }
}
