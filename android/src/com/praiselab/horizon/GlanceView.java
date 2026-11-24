package com.praiselab.horizon;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Clock + weather in the iOS widget style: two clean columns on a sky gradient.
 * Left: date, your time (big, thin), the second city's time. Right: place, temperature (big, thin), conditions.
 */
final class GlanceView extends HoldFrame {
    private static final Typeface SEMI = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface THIN = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final Launcher l;
    private final TextView date, time, ampm, zone, place, temp, cond, nextLine;
    private final ImageView icon;
    private final WeatherSky sky;
    private int animatedGlyph = -2;
    private long eventId = -1;
    private int glyph = -1;
    private String detail = "", town = "";

    GlanceView(Launcher l) {
        super(l);
        this.l = l;
        Sq.clip(this, U.dp(U.R_CARD));
        U.softShadow(this);

        sky = new WeatherSky(l);
        sky.onTick = this::tickIcon;
        addView(sky, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        LinearLayout outer = new LinearLayout(l);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(U.dp(20), U.dp(8), U.dp(20), U.dp(8));
        addView(outer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        LinearLayout row = new LinearLayout(l);
        outer.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        // your next calendar event, along the bottom
        nextLine = label(17, Color.WHITE);
        nextLine.setOnClickListener(v -> l.openOutlookCalendar());
        nextLine.setCompoundDrawablePadding(U.dp(6));
        nextLine.setPadding(0, 0, 0, U.dp(2));
        nextLine.setGravity(Gravity.CENTER);
        // the next event sits centred along the top of the card
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nl.gravity = Gravity.CENTER_HORIZONTAL;
        outer.addView(nextLine, 0, nl);

        // left column
        LinearLayout left = column(Gravity.START);
        date = label(17, Color.WHITE);
        left.addView(date);
        LinearLayout clock = new LinearLayout(l);
        clock.setGravity(Gravity.BOTTOM);
        time = number(48);
        clock.addView(time);
        ampm = label(16, 0xe6ffffff);
        ampm.setPadding(U.dp(3), 0, 0, U.dp(7));
        clock.addView(ampm);
        left.addView(clock);
        zone = label(15, 0xe6ffffff);
        zone.setIncludeFontPadding(false);
        left.addView(zone);
        row.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));

        // right column
        LinearLayout right = column(Gravity.END);
        place = label(17, Color.WHITE);
        place.setGravity(Gravity.END);
        right.addView(place);
        LinearLayout now = new LinearLayout(l);
        now.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        icon = new ImageView(l);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(U.dp(36), U.dp(36));
        il.rightMargin = U.dp(6);
        now.addView(icon, il);
        temp = number(48);
        now.addView(temp);
        right.addView(now);
        cond = label(15, 0xe6ffffff);
        cond.setGravity(Gravity.END);
        right.addView(cond);
        row.addView(right, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));

        time.setOnClickListener(v -> l.startSafely(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
        zone.setOnClickListener(v -> l.startSafely(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
        date.setOnClickListener(v -> l.openOutlookCalendar());
        // the whole clock side opens Alarms, the whole weather side opens the weather app
        View.OnClickListener alarms = v -> l.startSafely(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
        ((View) time.getParent()).setOnClickListener(alarms);
        ampm.setOnClickListener(alarms);
        ((View) zone.getParent()).setOnClickListener(alarms);
        View.OnClickListener wx = v -> l.onWeatherTap();
        right.setOnClickListener(wx);
        temp.setOnClickListener(wx);
        place.setOnClickListener(wx);
        cond.setOnClickListener(wx);
        icon.setOnClickListener(wx);
        ((View) temp.getParent()).setOnClickListener(wx);
        applyColors();
        update();
    }

    private LinearLayout column(int gravity) {
        LinearLayout c = new LinearLayout(l);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(gravity | Gravity.CENTER_VERTICAL);
        return c;
    }

    private TextView label(float sp, int color) {
        TextView t = U.text(l, sp, color, SEMI);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setShadowLayer(U.dp(4), 0, U.dp(1), 0x40000000);
        return t;
    }

    private TextView number(float sp) {
        TextView t = U.text(l, sp, Color.WHITE, THIN);
        t.setIncludeFontPadding(false);
        t.setLetterSpacing(-0.03f);
        t.setShadowLayer(U.dp(4), 0, U.dp(1), 0x33000000);
        return t;
    }

    /** iOS Weather colours: the sky follows the conditions and day / night. */
    void applyColors() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean night = hour < 6 || hour >= 19;
        int[] sky;
        int glyph = WeatherIcon.base(this.glyph); // partly cloudy, snow and fog share a sky with their base condition
        // dark-mode skies: deep, moody versions of each condition, so the card sits calmly on a dark Home
        if (glyph == Glyph.SUN) sky = new int[]{0xff0e2a5c, 0xff1a4a8f};
        else if (glyph == Glyph.MOON) sky = new int[]{0xff070d22, 0xff16214a};
        else if (glyph == Glyph.RAIN) sky = night ? new int[]{0xff0f141f, 0xff1d2738} : new int[]{0xff1e2938, 0xff34445a};
        else if (glyph == Glyph.FLASH) sky = new int[]{0xff100f17, 0xff26223a};
        else if (glyph == Glyph.CLOUD) sky = night ? new int[]{0xff12171f, 0xff252e3d} : new int[]{0xff283447, 0xff445269};
        else sky = night ? new int[]{0xff070d22, 0xff16214a} : new int[]{0xff0e2a5c, 0xff1a4a8f};
        boolean anim = l.prefs.sp.getBoolean("wxAnimated", false);
        this.sky.set(glyph, night, sky, anim);
        icon.setImageDrawable(this.glyph >= 0 ? new WeatherIcon(this.glyph) : null);
        icon.setVisibility(this.glyph >= 0 ? VISIBLE : GONE);
        animateIcon(anim ? glyph : -1);
    }

    /** The small weather symbol moves with the sky's own frames (no extra animation running at full refresh). */
    private void tickIcon(float t) {
        if (animatedGlyph == Glyph.SUN) icon.setRotation(t * 15f % 360f);
        else if (animatedGlyph >= 0) icon.setTranslationY((float) Math.sin(t * 2.4) * U.dp(1.5f));
    }

    private void animateIcon(int g) {
        animatedGlyph = g;
        icon.setRotation(0f);
        icon.setTranslationY(0f);
    }

    void update() {
        Date now = new Date();
        boolean h24 = DateFormat.is24HourFormat(l);
        android.content.SharedPreferences sp = l.prefs.sp;
        Typeface face = "bold".equals(sp.getString("clockWeight", "thin")) ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : THIN;
        time.setTypeface(face);
        temp.setTypeface(face);
        time.setText(DateFormat.format(h24 ? "H:mm" : "h:mm", now));
        ampm.setText(h24 ? "" : DateFormat.format("a", now).toString().toUpperCase(java.util.Locale.getDefault()));
        String next = nextEvent();
        // the date line becomes the next meeting when one is less than an hour away
        boolean events = sp.getBoolean("clockEvents", true), longDate = sp.getBoolean("dateLong", false);
        date.setText(DateFormat.format(longDate ? "EEEE, MMMM d" : "EEE, MMM d", now));
        nextLine.setText(next);
        nextLine.setVisibility(events && !next.isEmpty() ? VISIBLE : GONE);
        android.graphics.drawable.Drawable cal = new Glyph(Glyph.CHECK, 0xccffffff);
        cal.setBounds(0, 0, U.dp(17), U.dp(17));
        nextLine.setCompoundDrawables(cal, null, null, null);
        zone.setVisibility(sp.getBoolean("clockZone2", true) ? VISIBLE : GONE);
        place.setVisibility(sp.getBoolean("wxTown", true) ? VISIBLE : GONE);
        cond.setVisibility(sp.getBoolean("wxHiLo", true) ? VISIBLE : GONE);
        TimeZone tz = TimeZone.getTimeZone(l.prefs.sp.getString("zone2", "Asia/Kolkata"));
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat(h24 ? "H:mm" : "h:mm a", java.util.Locale.getDefault());
        f.setTimeZone(tz);
        Calendar here = Calendar.getInstance(), there = Calendar.getInstance(tz);
        int diff = there.get(Calendar.DAY_OF_YEAR) - here.get(Calendar.DAY_OF_YEAR);
        if (Math.abs(diff) > 1) diff = -Integer.signum(diff);
        // second clock: the city small, its time big and thin, "Tomorrow" small
        String city = cityOf(tz.getID()) + "  ", t2 = f.format(now), day = diff > 0 ? "  Tomorrow" : diff < 0 ? "  Yesterday" : "";
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(city + t2 + day);
        int a = city.length(), b = a + t2.length();
        sb.setSpan(new android.text.style.AbsoluteSizeSpan(26, true), a, b, 0);
        sb.setSpan(new android.text.style.TypefaceSpan(THIN), a, b, 0);
        sb.setSpan(new android.text.style.ForegroundColorSpan(Color.WHITE), a, b, 0);
        sb.setSpan(new android.text.style.AbsoluteSizeSpan(14, true), b, sb.length(), 0);
        zone.setText(sb);
        applyColors();
    }

    static String cityOf(String id) {
        String c = id.substring(id.lastIndexOf('/') + 1).replace('_', ' ');
        return c.equals("Kolkata") ? "India" : c;
    }

    /** text null hides the weather; askPermission shows a prompt to turn weather on. */
    void setWeather(String text, int g, boolean askPermission) {
        if (askPermission) { temp.setText(""); place.setText("Tap for weather"); glyph = -1; applyColors(); return; }
        temp.setText(text == null ? "" : text);
        glyph = text == null ? -1 : g;
        temp.setContentDescription(text == null ? null : "Weather " + text);
        applyColors();
    }

    /** "Sunny  H:30°  L:13°" -> "Sunny · H:30° L:13°" */
    void setWeatherDetail(String d) {
        detail = d == null ? "" : d.trim();
        cond.setText(detail.replaceAll("\\s+H:", "  H:").replaceAll("\\s+L:", " L:"));
        showPlace();
    }

    void setPlace(String p) { town = p == null ? "" : p; showPlace(); }

    private void showPlace() { place.setText(town.isEmpty() ? "My location" : town); }

    private String nextEvent() {
        Object[] r = next(l);
        eventId = (Long) r[1];
        return (String) r[0];
    }

    /** {"In 25 min · Title", eventId} for the next timed event in the next 24 hours, or {"", -1}. */
    static Object[] next(android.content.Context l) {
        if (l.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return new Object[]{"", -1L};
        long now = System.currentTimeMillis();
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, now - 6 * 3600_000L);
        ContentUris.appendId(b, now + 7 * 24 * 3600_000L);
        String[] proj = {CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.SELF_ATTENDEE_STATUS};
        String sel = CalendarContract.Instances.VISIBLE + "=1 AND " + CalendarContract.Instances.ALL_DAY + "=0 AND "
                + CalendarContract.Instances.END + ">" + now;
        try (Cursor c = l.getContentResolver().query(b.build(), proj, sel, null, CalendarContract.Instances.BEGIN + " ASC")) {
            while (c != null && c.moveToNext()) {
                if (c.getInt(4) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue;
                long id = c.getLong(0);
                String title = c.isNull(1) || c.getString(1).isEmpty() ? "(No title)" : c.getString(1);
                if (title.toLowerCase(java.util.Locale.ROOT).startsWith("canceled") || title.toLowerCase(java.util.Locale.ROOT).startsWith("cancelled")) continue;
                long begin = c.getLong(2);
                long mins = (begin - now) / 60_000L;
                String clock = DateFormat.getTimeFormat(l).format(new Date(begin));
                Calendar bc = Calendar.getInstance(), nc = Calendar.getInstance();
                bc.setTimeInMillis(begin);
                int days = bc.get(Calendar.DAY_OF_YEAR) - nc.get(Calendar.DAY_OF_YEAR) + (bc.get(Calendar.YEAR) - nc.get(Calendar.YEAR)) * 365;
                String when = begin <= now ? "Now" : mins < 60 ? "In " + Math.max(1, mins) + " min"
                        : days == 0 ? "Today " + clock : days == 1 ? "Tomorrow " + clock
                        : DateFormat.format("EEE", bc) + " " + clock;
                return new Object[]{when + " · " + title, id};
            }
        } catch (Exception ignored) { }
        return new Object[]{"", -1L};
    }
}
