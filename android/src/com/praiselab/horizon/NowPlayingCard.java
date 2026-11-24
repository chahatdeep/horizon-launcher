package com.praiselab.horizon;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Now Playing in the One UI style: the artwork fills the card under a dark veil; output device on top,
 * title and artist in the middle, a draggable seek bar with times, and shuffle / previous / play / next / save.
 * Every control talks to the player's own media session; extras (shuffle, save) appear only when the player offers them.
 */
final class NowPlayingCard extends HoldFrame implements Media.Listener {
    private final Launcher l;
    private final ImageView art, shuffle, prev, play, next, save, outputIcon, appIcon;
    private final TextView output, title, tL, tR;
    private final Seek seek;
    private final LinearLayout top;
    int solid; // set inside the Control menu: an opaque fallback when there's no artwork
    private PlaybackState.CustomAction shuffleAction, saveAction;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (isShown()) progress();
            if (Media.playing() && isAttachedToWindow()) postDelayed(this, isShown() ? 1000 : 3000);
        }
    };

    NowPlayingCard(Launcher l) {
        super(l);
        this.l = l;
        Sq.clip(this, U.dp(U.R_CARD));
        U.softShadow(this);
        setBackgroundColor(0xff1c1c1e);

        art = new ImageView(l);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(art, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        View veil = new View(l);
        // like One UI: the artwork shows through at the top, the bottom half sinks into a dark translucent band
        veil.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x8c000000, 0x80000000, 0xb3000000, 0xe0000000}));
        addView(veil, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(l);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setPadding(U.dp(18), U.dp(2), U.dp(12), U.dp(4));
        addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // output device (tap to switch speakers / headphones)
        top = new LinearLayout(l);
        top.setGravity(Gravity.CENTER_VERTICAL);
        output = U.text(l, 12, 0xccffffff, U.MEDIUM);
        output.setText("This phone");
        top.addView(output, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        outputIcon = new ImageView(l);
        outputIcon.setImageDrawable(new Icon(Icon.OUTPUT));
        outputIcon.setPadding(U.dp(7), U.dp(7), U.dp(7), U.dp(7));
        outputIcon.setContentDescription("Choose where audio plays");
        top.addView(outputIcon, new LinearLayout.LayoutParams(U.dp(28), U.dp(28)));
        col.addView(top);
        top.setOnClickListener(v -> openOutput());
        outputIcon.setOnClickListener(v -> openOutput());

        // app icon + "Title • Artist"
        LinearLayout titleRow = new LinearLayout(l);
        titleRow.setGravity(Gravity.CENTER);
        appIcon = new ImageView(l);
        titleRow.addView(appIcon, new LinearLayout.LayoutParams(U.dp(18), U.dp(18)));
        title = U.text(l, 20, Color.WHITE, U.MEDIUM);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(U.dp(8), 0, 0, 0);
        titleRow.addView(title);
        col.addView(titleRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // seek bar with the times at its ends
        seek = new Seek();
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, U.dp(22));
        sl.topMargin = U.dp(2);
        col.addView(seek, sl);
        LinearLayout times = new LinearLayout(l);
        tL = U.text(l, 11, 0xb3ffffff, U.REGULAR);
        tR = U.text(l, 11, 0xb3ffffff, U.REGULAR);
        tR.setGravity(Gravity.END);
        times.addView(tL, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        times.addView(tR, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        times.setPadding(U.dp(2), 0, U.dp(2), 0);
        col.addView(times);

        // shuffle · previous · play · next · save
        LinearLayout controls = new LinearLayout(l);
        controls.setGravity(Gravity.CENTER);
        shuffle = button(new Icon(Icon.SHUFFLE), "Shuffle", 40, false);
        prev = button(new Icon(Icon.PREV), "Previous track", 42, true);
        play = button(new Glyph(Glyph.PLAY, Color.WHITE), "Play", 42, true);
        next = button(new Icon(Icon.NEXT), "Next track", 42, true);
        save = button(new Icon(Icon.ADD), "Save to your library", 40, false);
        for (View b : new View[]{shuffle, prev, play, next, save}) controls.addView(b);
        col.addView(controls, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // equal space above, between and below the rows (output · title · seek+times · buttons)
        int[] gaps = {5, 4, 2, 1, 0};
        for (int at : gaps) col.addView(new View(l), at, new LinearLayout.LayoutParams(1, 0, 1f));

        play.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            MediaController m = Media.current;
            if (m == null) { openPlayer(); return; }
            // right after a press the player may not have reported its new state yet: trust the last press
            boolean playingNow = android.os.SystemClock.uptimeMillis() - lastToggle < 1500 ? lastWantPlaying : Media.playing();
            if (playingNow) m.getTransportControls().pause(); else m.getTransportControls().play();
            lastWantPlaying = !playingNow;
            lastToggle = android.os.SystemClock.uptimeMillis();
            play.setImageDrawable(new Glyph(lastWantPlaying ? Glyph.PAUSE : Glyph.PLAY, Color.WHITE));
        });
        prev.setOnClickListener(v -> Media.skip(l, v, false));
        next.setOnClickListener(v -> Media.skip(l, v, true));
        shuffle.setOnClickListener(v -> custom(v, shuffleAction));
        save.setOnClickListener(v -> custom(v, saveAction));
        setOnClickListener(v -> openPlayer());
        setClickable(true);
    }

    private float downX, downY;
    private long lastToggle;
    private boolean lastWantPlaying;
    private long downAt;

    /** A plain tap anywhere on the card (outside the buttons) opens the music app. */
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        super.onTouchEvent(e); // long-press detection
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY(); downAt = e.getEventTime();
                break;
            case MotionEvent.ACTION_UP:
                boolean still = Math.hypot(e.getX() - downX, e.getY() - downY) < U.dp(10);
                boolean quick = e.getEventTime() - downAt < android.view.ViewConfiguration.getLongPressTimeout();
                if (still && quick) { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); openPlayer(); }
                break;
        }
        return true;
    }

    private ImageView button(Drawable icon, String desc, int sizeDp, boolean disc) {
        ImageView b = new ImageView(l);
        b.setImageDrawable(icon);
        b.setContentDescription(desc);
        int s = U.dp(sizeDp), pad = Math.round(s * 0.32f);
        b.setPadding(pad, pad, pad, pad);
        if (disc) {
            GradientDrawable o = new GradientDrawable();
            o.setShape(GradientDrawable.OVAL);
            o.setColor(0x2effffff);
            b.setBackground(o);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
        lp.setMargins(U.dp(9), 0, U.dp(9), 0);
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        applySize();
        // very short cards: smaller buttons, so the song title is never pushed out of the top
        int b = h < U.dp(78) ? 34 : 42, side = h < U.dp(78) ? 32 : 40;
        for (View v : new View[]{prev, play, next}) resize(v, b);
        for (View v : new View[]{shuffle, save}) resize(v, side);
        post(this::requestLayout); // visibility changed during layout: measure again
    }

    private boolean idleNow;

    /**
     * What fits decides what shows: everything at ~170dp; shorter cards drop the output row, then the times,
     * then the seek bar (title + seek + buttons need ~100dp), so the song title and buttons are never cut.
     * Nothing playing keeps the space (invisible) only where it fits.
     */
    private void applySize() {
        int h = getHeight();
        if (h == 0) return;
        top.setVisibility(h < U.dp(122) || !l.prefs.sp.getBoolean("npOutput", true) ? GONE : VISIBLE);
        int shown = idleNow ? INVISIBLE : VISIBLE;
        seek.setVisibility(h < U.dp(100) ? GONE : shown);
        tL.setVisibility(h < U.dp(104) ? GONE : shown);
        tR.setVisibility(h < U.dp(104) ? GONE : shown);
    }

    private static void resize(View v, int dp) {
        android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
        int s = U.dp(dp);
        if (lp.width == s) return;
        lp.width = s;
        lp.height = s;
        int pad = Math.round(s * 0.32f);
        v.setPadding(pad, pad, pad, pad);
        v.setLayoutParams(lp);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Media.listeners.add(this);
        onMedia();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Media.listeners.remove(this);
        removeCallbacks(ticker);
    }

    @Override
    public void onMedia() {
        MediaController m = Media.current;
        MediaMetadata md = m != null ? m.getMetadata() : null;
        boolean has = md != null && md.getString(MediaMetadata.METADATA_KEY_TITLE) != null;
        if (!Media.allowed && !Badges.allowed(l)) {
            idle("See what's playing", "Allow notification access for Horizon");
            return;
        }
        if (!has) {
            Apps.App sp = l.apps.first("com.spotify.music");
            idle("Nothing playing", sp != null ? "Tap to open Spotify" : "Play music in any app");
            return;
        }
        setAlpha(1f);
        Bitmap a = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (a == null) a = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        art.setImageBitmap(l.prefs.sp.getBoolean("npArt", true) ? a : null);
        setBackgroundColor(solid != 0 && a == null ? solid : 0xff1c1c1e);
        String t = md.getString(MediaMetadata.METADATA_KEY_TITLE), ar = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        title.setText(ar == null || ar.isEmpty() ? t : t + "  •  " + ar);
        Apps.App app = l.apps.first(m.getPackageName());
        appIcon.setImageBitmap(app != null ? l.apps.icon(app) : null);
        appIcon.setVisibility(app != null ? VISIBLE : GONE);
        seek.setColor(vivid(a));
        play.setImageDrawable(new Glyph(Media.playing() ? Glyph.PAUSE : Glyph.PLAY, Color.WHITE));
        play.setContentDescription(Media.playing() ? "Pause" : "Play");
        // extras the player offers (Spotify: shuffle and add/remove from library)
        shuffleAction = action(m, "shuffle");
        saveAction = action(m, "collection", "like", "favorite", "heart", "save");
        boolean extras = l.prefs.sp.getBoolean("npExtras", true);
        shuffle.setVisibility(extras && shuffleAction != null ? VISIBLE : INVISIBLE);
        save.setVisibility(extras && saveAction != null ? VISIBLE : INVISIBLE);
        boolean saved = saveAction != null && String.valueOf(saveAction.getName()).toLowerCase(Locale.ROOT).contains("remove");
        save.setImageDrawable(new Icon(saved ? Icon.SAVED : Icon.ADD));
        save.setContentDescription(saved ? "Remove from your library" : "Save to your library");
        for (View v : new View[]{prev, next}) v.setVisibility(VISIBLE);
        idleNow = false;
        applySize();
        output.setText(outputName(m));
        progress();
        removeCallbacks(ticker);
        if (Media.playing()) postDelayed(ticker, 1000);
    }

    private void idle(String head, String sub) {
        art.setImageBitmap(null);
        setBackgroundColor(solid != 0 ? solid : 0xff1c1c1e);
        title.setText(head + "  •  " + sub);
        appIcon.setVisibility(GONE);
        for (View v : new View[]{prev, next, shuffle, save}) v.setVisibility(INVISIBLE);
        idleNow = true;
        applySize();
        play.setImageDrawable(new Glyph(Glyph.PLAY, Color.WHITE));
        output.setText("This phone");
        if (solid == 0) setAlpha(0.75f);
    }

    private void progress() {
        MediaController m = Media.current;
        PlaybackState s = m != null ? m.getPlaybackState() : null;
        MediaMetadata md = m != null ? m.getMetadata() : null;
        long dur = md != null ? md.getLong(MediaMetadata.METADATA_KEY_DURATION) : 0;
        if (s == null || dur <= 0) { seek.set(0); tL.setText(""); tR.setText(""); return; }
        long pos = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING)
            pos += (long) ((android.os.SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
        pos = Math.max(0, Math.min(dur, pos));
        if (!seek.dragging) seek.set(pos / (float) dur);
        tL.setText(time(seek.dragging ? (long) (seek.value * dur) : pos));
        tR.setText(time(dur));
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
    }

    private static PlaybackState.CustomAction action(MediaController m, String... words) {
        PlaybackState s = m.getPlaybackState();
        if (s == null) return null;
        for (PlaybackState.CustomAction a : s.getCustomActions()) {
            String n = String.valueOf(a.getName()).toLowerCase(Locale.ROOT);
            for (String w : words) if (n.contains(w)) return a;
        }
        return null;
    }

    private void custom(View v, PlaybackState.CustomAction a) {
        MediaController m = Media.current;
        if (m == null || a == null) return;
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        m.getTransportControls().sendCustomAction(a, null);
    }

    /** "This phone", the Bluetooth device's name, or "Casting". */
    private String outputName(MediaController m) {
        if (m.getPlaybackInfo() != null && m.getPlaybackInfo().getPlaybackType() == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE) return "Casting";
        AudioManager am = l.getSystemService(AudioManager.class);
        if (am != null) {
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == AudioDeviceInfo.TYPE_BLE_HEADSET || t == AudioDeviceInfo.TYPE_BLE_SPEAKER
                        || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_USB_HEADSET) {
                    CharSequence n = d.getProductName();
                    return n != null && n.length() > 0 ? n.toString() : "Headphones";
                }
            }
        }
        return "This phone";
    }

    /** The system's "play on" picker (speakers, earbuds, cast); Bluetooth settings if the phone doesn't offer it. */
    private void openOutput() {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        // Android 14+: the system's own "play on" picker (the same one the notification shade opens)
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            try { if (android.media.MediaRouter2.getInstance(l).showSystemOutputSwitcher()) return; } catch (Exception ignored) { }
        }
        Intent i = new Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
                .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", Media.current != null ? Media.current.getPackageName() : l.getPackageName());
        try { l.startActivity(i); }
        catch (Exception e) { l.startSafely(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
    }

    private void openPlayer() {
        MediaController m = Media.current;
        if (!Media.allowed && !Badges.allowed(l)) {
            l.startSafely(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, new ComponentName(l, BadgeService.class).flattenToString()));
            return;
        }
        if (m != null && m.getSessionActivity() != null) {
            try {
                // Android 14+: Horizon is in front, so it vouches for the player opening itself
                android.app.ActivityOptions o = android.app.ActivityOptions.makeBasic();
                if (android.os.Build.VERSION.SDK_INT >= 34)
                    o.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                m.getSessionActivity().send(l, 0, null, null, null, null, o.toBundle());
                return;
            } catch (Exception ignored) { }
        }
        Apps.App a = l.apps.first(m != null ? m.getPackageName() : "com.spotify.music");
        if (a != null) l.launchApp(a, this);
    }

    /** A bright, saturated version of the artwork's colour for the seek bar. */
    private static int vivid(Bitmap a) {
        if (a == null) return 0xffffd60a;
        Bitmap s = Bitmap.createScaledBitmap(a, 6, 6, true);
        float best = -1; int pick = 0xffffd60a;
        float[] hsv = new float[3];
        for (int x = 0; x < 6; x++) for (int y = 0; y < 6; y++) {
            Color.colorToHSV(s.getPixel(x, y), hsv);
            float score = hsv[1] * hsv[2];
            if (score > best) { best = score; pick = s.getPixel(x, y); }
        }
        Color.colorToHSV(pick, hsv);
        if (hsv[1] < 0.25f) return 0xffffffff;
        hsv[1] = Math.max(0.65f, hsv[1]);
        hsv[2] = Math.max(0.9f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    /** Draggable seek bar: drag the thumb (or tap the track) to jump within the song. */
    private final class Seek extends View {
        float value;
        boolean dragging;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), knob = new Paint(Paint.ANTI_ALIAS_FLAG),
                ring = new Paint(Paint.ANTI_ALIAS_FLAG);

        Seek() {
            super(l);
            track.setColor(0x4dffffff);
            ring.setColor(Color.WHITE);
            fill.setColor(0xffffd60a);
            knob.setColor(0xffffd60a);
            setContentDescription("Song position");
        }

        void setColor(int c) { fill.setColor(c); knob.setColor(c); invalidate(); }
        void set(float v) { value = U.clamp(v, 0f, 1f); invalidate(); }

        @Override
        protected void onDraw(Canvas c) {
            float r = U.dp(9), h = U.dp(4), cy = getHeight() / 2f, x0 = r, x1 = getWidth() - r, x = x0 + (x1 - x0) * value;
            c.drawRoundRect(x0, cy - h / 2, x1, cy + h / 2, h / 2, h / 2, track);
            c.drawRoundRect(x0, cy - h / 2, x, cy + h / 2, h / 2, h / 2, fill);
            float kr = dragging ? r * 1.15f : r;
            c.drawCircle(x, cy, kr, ring);
            c.drawCircle(x, cy, kr - U.dp(2.5f), knob);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float r = U.dp(9), v = U.clamp((e.getX() - r) / Math.max(1f, getWidth() - 2 * r), 0f, 1f);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    dragging = true;
                    set(v);
                    progress();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    set(v);
                    progress();
                    return true;
                case MotionEvent.ACTION_UP:
                    dragging = false;
                    MediaController m = Media.current;
                    MediaMetadata md = m != null ? m.getMetadata() : null;
                    long dur = md != null ? md.getLong(MediaMetadata.METADATA_KEY_DURATION) : 0;
                    if (m != null && dur > 0) {
                        m.getTransportControls().seekTo((long) (v * dur));
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    }
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    invalidate();
                    return true;
            }
            return false;
        }
    }

    /** Line icons the stock glyph set doesn't have: shuffle, output device, add and saved. */
    private static final class Icon extends Drawable {
        static final int SHUFFLE = 0, OUTPUT = 1, ADD = 2, SAVED = 3, PREV = 4, NEXT = 5;
        private final int kind;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Icon(int kind) {
            this.kind = kind;
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height()), x = b.left, y = b.top;
            p.setStrokeWidth(s * 0.09f);
            path.reset();
            switch (kind) {
                case SHUFFLE:
                    path.moveTo(x + s * 0.1f, y + s * 0.28f); path.cubicTo(x + s * 0.5f, y + s * 0.28f, x + s * 0.5f, y + s * 0.72f, x + s * 0.88f, y + s * 0.72f);
                    path.moveTo(x + s * 0.1f, y + s * 0.72f); path.cubicTo(x + s * 0.5f, y + s * 0.72f, x + s * 0.5f, y + s * 0.28f, x + s * 0.88f, y + s * 0.28f);
                    path.moveTo(x + s * 0.74f, y + s * 0.14f); path.lineTo(x + s * 0.88f, y + s * 0.28f); path.lineTo(x + s * 0.74f, y + s * 0.42f);
                    path.moveTo(x + s * 0.74f, y + s * 0.58f); path.lineTo(x + s * 0.88f, y + s * 0.72f); path.lineTo(x + s * 0.74f, y + s * 0.86f);
                    c.drawPath(path, p);
                    break;
                case PREV:
                case NEXT: {
                    // a filled triangle against a bar: skip to start / skip to end
                    boolean fwd = kind == NEXT;
                    p.setStyle(Paint.Style.FILL);
                    float bx = fwd ? x + s * 0.74f : x + s * 0.18f;
                    c.drawRoundRect(bx, y + s * 0.2f, bx + s * 0.09f, y + s * 0.8f, s * 0.04f, s * 0.04f, p);
                    if (fwd) { path.moveTo(x + s * 0.2f, y + s * 0.2f); path.lineTo(x + s * 0.68f, y + s * 0.5f); path.lineTo(x + s * 0.2f, y + s * 0.8f); }
                    else { path.moveTo(x + s * 0.8f, y + s * 0.2f); path.lineTo(x + s * 0.32f, y + s * 0.5f); path.lineTo(x + s * 0.8f, y + s * 0.8f); }
                    path.close();
                    c.drawPath(path, p);
                    p.setStyle(Paint.Style.STROKE);
                    break;
                }
                case OUTPUT:
                    c.drawCircle(x + s * 0.5f, y + s * 0.5f, s * 0.4f, p);
                    p.setStyle(Paint.Style.FILL);
                    path.moveTo(x + s * 0.42f, y + s * 0.34f); path.lineTo(x + s * 0.66f, y + s * 0.5f); path.lineTo(x + s * 0.42f, y + s * 0.66f); path.close();
                    c.drawPath(path, p);
                    p.setStyle(Paint.Style.STROKE);
                    break;
                case ADD:
                    c.drawCircle(x + s * 0.5f, y + s * 0.5f, s * 0.42f, p);
                    c.drawLine(x + s * 0.5f, y + s * 0.3f, x + s * 0.5f, y + s * 0.7f, p);
                    c.drawLine(x + s * 0.3f, y + s * 0.5f, x + s * 0.7f, y + s * 0.5f, p);
                    break;
                default: // SAVED: filled circle with a check
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(x + s * 0.5f, y + s * 0.5f, s * 0.44f, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setColor(0xff111111);
                    path.moveTo(x + s * 0.3f, y + s * 0.52f); path.lineTo(x + s * 0.44f, y + s * 0.65f); path.lineTo(x + s * 0.71f, y + s * 0.37f);
                    c.drawPath(path, p);
                    p.setColor(Color.WHITE);
            }
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
