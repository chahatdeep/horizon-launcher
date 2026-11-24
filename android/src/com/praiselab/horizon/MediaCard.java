package com.praiselab.horizon;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Now Playing for Spotify or any media app: artwork-tinted card with progress and transport controls. */
final class MediaCard extends HoldFrame implements Media.Listener {
    private final Launcher l;
    private final LinearLayout row, info, controls, chip, times;
    private final ImageView art, prev, play, next, appIcon, backdrop;
    private final TextView appName, title, artist, tL, tR;
    private final Bar bar;
    private final Sq bg = new Sq(0, U.dp(U.R_CARD));
    private boolean tall;
    /** When set (inside the Control menu), the idle card uses this opaque color instead of the tile color. */
    int solid;
    private int textColor = Color.WHITE;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!isShown()) { if (Media.playing() && isAttachedToWindow()) postDelayed(this, 3000); return; } // off-screen: no work
            progress();
            if (Media.playing() && isAttachedToWindow()) postDelayed(this, 1000);
        }
    };

    MediaCard(Launcher l) {
        super(l);
        this.l = l;
        setBackground(bg);
        Sq.clip(this, U.dp(U.R_CARD));
        setClipToPadding(false); // the blurred artwork runs edge to edge
        U.softShadow(this);
        int pad = U.dp(14);
        setPadding(pad, pad, pad, pad);

        // Apple Music style: the artwork itself, heavily blurred and dimmed, fills the card
        backdrop = new ImageView(l);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setScaleX(1.25f);
        backdrop.setScaleY(1.25f);
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            android.graphics.ColorMatrix dim = new android.graphics.ColorMatrix();
            dim.setScale(0.55f, 0.55f, 0.55f, 1f);
            android.graphics.ColorMatrix sat = new android.graphics.ColorMatrix();
            sat.setSaturation(1.35f);
            dim.preConcat(sat);
            backdrop.setRenderEffect(android.graphics.RenderEffect.createChainEffect(
                    android.graphics.RenderEffect.createColorFilterEffect(new android.graphics.ColorMatrixColorFilter(dim)),
                    android.graphics.RenderEffect.createBlurEffect(U.dp(40), U.dp(40), android.graphics.Shader.TileMode.CLAMP)));
        }
        backdrop.setVisibility(GONE);
        FrameLayout.LayoutParams bdl = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        bdl.setMargins(-pad, -pad, -pad, -pad);
        addView(backdrop, bdl);

        row = new LinearLayout(l);
        addView(row, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        art = new ImageView(l);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Sq.clip(art, U.dp(13));
        row.addView(art);

        info = new LinearLayout(l);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(info);

        chip = new LinearLayout(l);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        appIcon = new ImageView(l);
        chip.addView(appIcon, new LinearLayout.LayoutParams(U.dp(16), U.dp(16)));
        appName = U.text(l, 12, Color.WHITE, U.MEDIUM);
        appName.setPadding(U.dp(6), 0, 0, 0);
        appName.setSingleLine(true);
        chip.addView(appName);
        info.addView(chip);

        title = U.text(l, 17, Color.WHITE, U.MEDIUM);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, U.dp(6), 0, 0);
        info.addView(title);
        artist = U.text(l, 14, Color.WHITE, U.REGULAR);
        artist.setSingleLine(true);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        artist.setPadding(0, U.dp(3), 0, 0);
        info.addView(artist);

        bar = new Bar(l);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, U.dp(5));
        blp.topMargin = U.dp(10);
        info.addView(bar, blp);
        times = new LinearLayout(l);
        tL = U.text(l, 11, Color.WHITE, U.REGULAR);
        tR = U.text(l, 11, Color.WHITE, U.REGULAR);
        tR.setGravity(Gravity.END);
        times.addView(tL, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        times.addView(tR, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        times.setPadding(0, U.dp(4), 0, 0);
        info.addView(times);

        controls = new LinearLayout(l);
        controls.setGravity(Gravity.CENTER);
        prev = control(Glyph.PREV, U.dp(40));
        play = control(Glyph.PLAY, U.dp(48));
        next = control(Glyph.NEXT, U.dp(40));
        info.addView(controls, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, U.dp(52)));

        prev.setOnClickListener(v -> skip(v, false));
        next.setOnClickListener(v -> skip(v, true));
        prev.setContentDescription("Previous track");
        next.setContentDescription("Next track");
        play.setOnClickListener(v -> {
            MediaController m = Media.current;
            if (m == null) { openPlayer(); return; }
            if (Media.playing()) m.getTransportControls().pause(); else m.getTransportControls().play();
        });
        setOnClickListener(v -> openPlayer());
        setClickable(true);
    }

    /**
     * Skips a track. Some players ignore the skip command while paused or in certain modes, so if the song hasn't
     * changed half a second later, the same media key a headset button sends is used instead.
     */
    private void skip(View v, boolean forward) {
        v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        MediaController m = Media.current;
        String before = m != null && m.getMetadata() != null ? String.valueOf(m.getMetadata().getDescription().getTitle()) : null;
        if (m != null) {
            if (forward) m.getTransportControls().skipToNext(); else m.getTransportControls().skipToPrevious();
        }
        postDelayed(() -> {
            MediaController now = Media.current;
            String after = now != null && now.getMetadata() != null ? String.valueOf(now.getMetadata().getDescription().getTitle()) : null;
            if (m != null && before != null && !before.equals(after)) return; // it worked
            int key = forward ? android.view.KeyEvent.KEYCODE_MEDIA_NEXT : android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS;
            android.media.AudioManager am = l.getSystemService(android.media.AudioManager.class);
            if (now != null) {
                now.dispatchMediaButtonEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key));
                now.dispatchMediaButtonEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key));
                // nothing queued after this song (e.g. played alone from search): Next keeps the music going
                // with the player's own "radio" for this song, instead of restarting it
                if (forward) postDelayed(() -> startRadioIfStuck(before), 900);
            } else if (am != null) {
                am.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key));
                am.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key));
            }
        }, 500);
    }

    /** With nothing playing the card steps back: dimmer, no progress bar or skip buttons. */
    private void idle(boolean on) {
        if (solid != 0) return; // the Control menu copy stays as it is
        setAlpha(on ? 0.7f : 1f);
        bar.setVisibility(on ? GONE : VISIBLE);
        prev.setVisibility(on ? GONE : VISIBLE);
        next.setVisibility(on ? GONE : VISIBLE);
    }

    private void startRadioIfStuck(String before) {
        MediaController m = Media.current;
        if (m == null || m.getMetadata() == null || m.getPlaybackState() == null) return;
        String now = String.valueOf(m.getMetadata().getDescription().getTitle());
        if (before == null || !before.equals(now)) return;
        for (android.media.session.PlaybackState.CustomAction a : m.getPlaybackState().getCustomActions()) {
            if (String.valueOf(a.getName()).toLowerCase(java.util.Locale.ROOT).contains("radio")) {
                m.getTransportControls().sendCustomAction(a, null);
                U.toast(l, "No next song queued, so playing radio based on this one");
                return;
            }
        }
        U.toast(l, "This is the only song queued. Play an album or playlist to skip ahead");
    }

    private ImageView control(int glyph, int size) {
        ImageView b = new ImageView(l);
        b.setPadding(size / 4, size / 4, size / 4, size / 4);
        b.setTag(glyph);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(U.dp(8), 0, U.dp(8), 0);
        controls.addView(b, lp);
        return b;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!Media.listeners.contains(this)) Media.listeners.add(this);
        if (Badges.allowed(l)) Media.start(l);
        onMedia();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Media.listeners.remove(this);
        removeCallbacks(ticker);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        boolean t = h > U.dp(250);
        if (t != tall || ow == 0) { tall = t; post(this::applyLayout); }
    }

    private void applyLayout() {
        int w = getWidth() - getPaddingLeft() - getPaddingRight(), h = getHeight() - getPaddingTop() - getPaddingBottom();
        row.setOrientation(tall ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        int side = tall ? Math.max(U.dp(80), Math.min(w, h - U.dp(170))) : Math.max(U.dp(48), Math.min(h, U.dp(150)));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(side, side);
        alp.gravity = tall ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL;
        art.setLayoutParams(alp);
        LinearLayout.LayoutParams ilp = tall ? new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1)
                : new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
        if (tall) ilp.topMargin = U.dp(12); else ilp.leftMargin = U.dp(14);
        info.setLayoutParams(ilp);
        // short cards keep the transport controls, beside the title instead of underneath it
        boolean mini = !tall && h < U.dp(124);
        if (controls.getParent() != null) ((android.view.ViewGroup) controls.getParent()).removeView(controls);
        // round, thumb-sized buttons (at least 48dp to hit), with even gaps
        int big = mini ? U.dp(56) : U.dp(60), small = mini ? U.dp(48) : U.dp(52);
        for (int k = 0; k < controls.getChildCount(); k++) {
            View b = controls.getChildAt(k);
            int s = b == play ? big : small;
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(s, s);
            clp.setMargins(U.dp(4), 0, U.dp(4), 0);
            b.setLayoutParams(clp);
            int pad = b == play ? s * 3 / 10 : s * 7 / 25;
            b.setPadding(pad, pad, pad, pad);
        }
        if (mini) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
            lp.leftMargin = U.dp(4);
            lp.rightMargin = U.dp(6); // in from the edge
            row.addView(controls, lp);
        } else {
            info.addView(controls, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, U.dp(52)));
        }
        controls.setVisibility(VISIBLE);
        times.setVisibility(mini ? GONE : VISIBLE);
        title.setTextSize(tall ? 20 : mini ? 15 : 16);
    }

    @Override
    public void onMedia() {
        U.Pal p = l.pal;
        MediaController m = Media.current;
        Bitmap a = null;
        if (!Media.allowed && !Badges.allowed(l)) {
            show(p, null, "See what's playing", "Allow notification access for Horizon", null, null);
        } else if (m == null || m.getMetadata() == null
                || (m.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE) == null && !Media.playing())) {
            Apps.App sp = l.apps.first("com.spotify.music");
            show(p, null, "Nothing playing", sp != null ? "Tap to open Spotify" : "Play music in any app", sp != null ? sp.pkg : null, null);
            idle(true);
            return;
        } else {
            idle(false);
            MediaMetadata md = m.getMetadata();
            a = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (a == null) a = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (a == null) a = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            String t = md.getString(MediaMetadata.METADATA_KEY_TITLE);
            String ar = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (ar == null) ar = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            show(p, a, t != null ? t : "Unknown title", ar != null ? ar : "", m.getPackageName(), md);
        }
        removeCallbacks(ticker);
        progress();
        if (Media.playing()) postDelayed(ticker, 1000);
    }

    private void show(U.Pal p, Bitmap a, String t, String sub, String pkg, MediaMetadata md) {
        int base;
        if (a != null) {
            base = tint(a);
            textColor = Color.WHITE;
        } else {
            base = solid != 0 ? solid : p.tile;
            textColor = solid != 0 ? p.fg : (p.colorTiles ? 0xffffffff : p.tileFg);
        }
        bg.setColor(base);
        bg.setStroke(0, 0); // borderless, like the other cards
        if (a != null) art.setImageBitmap(a);
        else {
            art.setImageDrawable(new Glyph(Glyph.MUSIC, textColor));
            art.setBackground(U.round(U.alpha(textColor, 0.12f), 13));
            int ip = U.dp(18);
            art.setPadding(ip, ip, ip, ip);
        }
        if (a != null) { art.setBackground(null); art.setPadding(0, 0, 0, 0); }
        boolean blur = a != null && solid == 0 && android.os.Build.VERSION.SDK_INT >= 31;
        backdrop.setVisibility(blur ? VISIBLE : GONE);
        if (blur) backdrop.setImageBitmap(Bitmap.createScaledBitmap(a, 48, 48, true)); // tiny: it is blurred anyway
        title.setText(t);
        artist.setText(sub);
        Apps.App app = pkg != null ? l.apps.first(pkg) : null;
        chip.setVisibility(app != null ? VISIBLE : GONE);
        if (app != null) { appIcon.setImageBitmap(l.apps.icon(app)); appName.setText(app.label); }
        for (TextView v : new TextView[]{appName, title, tL, tR}) v.setTextColor(textColor);
        artist.setTextColor(U.alpha(textColor, 0.78f));
        bar.color = textColor;
        boolean hasSession = md != null;
        bar.setVisibility(hasSession ? VISIBLE : INVISIBLE);
        tL.setVisibility(hasSession ? VISIBLE : INVISIBLE);
        tR.setVisibility(hasSession ? VISIBLE : INVISIBLE);
        controls.setVisibility(VISIBLE);
        prev.setImageDrawable(new Glyph(Glyph.PREV, textColor));
        next.setImageDrawable(new Glyph(Glyph.NEXT, textColor));
        // frosted circles behind previous / next, so they read as buttons
        for (ImageView b : new ImageView[]{prev, next}) {
            android.graphics.drawable.GradientDrawable o = new android.graphics.drawable.GradientDrawable();
            o.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            o.setColor(U.alpha(textColor, 0.16f));
            b.setBackground(o);
        }
        play.setImageDrawable(new Glyph(Media.playing() ? Glyph.PAUSE : Glyph.PLAY, a != null ? base | 0xff000000 : p.onAccent));
        android.graphics.drawable.GradientDrawable pb = new android.graphics.drawable.GradientDrawable();
        pb.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        pb.setColor(a != null ? Color.WHITE : p.accent);
        play.setBackground(pb);
        prev.setAlpha(hasSession ? 1f : 0.35f);
        next.setAlpha(hasSession ? 1f : 0.35f);
    }

    private void progress() {
        MediaController m = Media.current;
        PlaybackState s = m != null ? m.getPlaybackState() : null;
        MediaMetadata md = m != null ? m.getMetadata() : null;
        long dur = md != null ? md.getLong(MediaMetadata.METADATA_KEY_DURATION) : 0;
        if (s == null || dur <= 0) { bar.frac = 0; bar.invalidate(); tL.setText(""); tR.setText(""); return; }
        long pos = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING)
            pos += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
        pos = Math.max(0, Math.min(dur, pos));
        bar.frac = pos / (float) dur;
        bar.invalidate();
        tL.setText(clock(pos));
        tR.setText("-" + clock(dur - pos));
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        return (s / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60);
    }

    /** Artwork's average color, darkened enough for white text to stay readable. */
    private static int tint(Bitmap a) {
        Bitmap s = Bitmap.createScaledBitmap(a, 8, 8, true);
        long r = 0, g = 0, b = 0;
        for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) {
            int c = s.getPixel(x, y);
            r += Color.red(c); g += Color.green(c); b += Color.blue(c);
        }
        float[] hsv = new float[3];
        Color.RGBToHSV((int) (r / 64), (int) (g / 64), (int) (b / 64), hsv);
        hsv[1] = Math.min(0.8f, hsv[1] * 1.25f); // richer, deeper colour, Apple Music style
        hsv[2] = Math.min(0.32f, Math.max(0.18f, hsv[2]));
        return U.alpha(Color.HSVToColor(hsv), 0.94f);
    }

    private void openPlayer() {
        MediaController m = Media.current;
        if (!Media.allowed && !Badges.allowed(l)) {
            Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, new ComponentName(l, BadgeService.class).flattenToString());
            l.startSafely(i);
            return;
        }
        if (m != null) {
            PendingIntent pi = m.getSessionActivity();
            if (pi != null) { try { pi.send(); return; } catch (Exception ignored) { } }
            Apps.App a = l.apps.first(m.getPackageName());
            if (a != null) { l.launchApp(a, this); return; }
        }
        Apps.App sp = l.apps.first("com.spotify.music");
        if (sp != null) l.launchApp(sp, this);
    }

    /** Thin rounded progress track. */
    private static final class Bar extends View {
        float frac;
        int color = Color.WHITE;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Bar(android.content.Context c) { super(c); }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight(), r = h / 2f;
            p.setColor(U.alpha(color, 0.28f));
            c.drawRoundRect(new RectF(0, 0, getWidth(), h), r, r, p);
            p.setColor(color);
            c.drawRoundRect(new RectF(0, 0, Math.max(h, getWidth() * frac), h), r, r, p);
        }
    }
}
