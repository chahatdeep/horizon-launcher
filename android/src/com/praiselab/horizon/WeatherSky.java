package com.praiselab.horizon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * A live sky behind the clock & weather card: sun glow and turning rays, twinkling stars, drifting clouds,
 * falling rain, the odd lightning flash. Runs at ~20 fps only while the card is actually on screen; still otherwise.
 */
final class WeatherSky extends View {
    private int glyph = -1;
    private boolean night, animate = true, windowVisible = true;
    private final Paint base = new Paint(), p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] stars = new float[3 * 34], drops = new float[3 * 46];
    private int[] colors = {0xff1c1c1e, 0xff2e3036};
    private long flashAt;
    private final Random rnd = new Random(7);
    private RadialGradient glow, moon;
    private int glowFor = -1;
    /** Called on each animation frame (about 20 a second) with the time in seconds, so the small icon moves in step. */
    java.util.function.Consumer<Float> onTick;

    WeatherSky(Context c) {
        super(c);
        for (int i = 0; i < stars.length; i += 3) { stars[i] = rnd.nextFloat(); stars[i + 1] = rnd.nextFloat() * 0.8f; stars[i + 2] = rnd.nextFloat() * 6.28f; }
        for (int i = 0; i < drops.length; i += 3) { drops[i] = rnd.nextFloat(); drops[i + 1] = rnd.nextFloat(); drops[i + 2] = 0.7f + rnd.nextFloat() * 0.6f; }
    }

    void set(int glyph, boolean night, int[] colors, boolean animate) {
        this.glyph = glyph;
        this.night = night;
        this.colors = colors;
        this.animate = animate;
        base.setShader(null);
        invalidate();
    }

    @Override
    protected void onWindowVisibilityChanged(int v) {
        super.onWindowVisibilityChanged(v);
        windowVisible = v == VISIBLE;
        if (windowVisible) invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        base.setShader(null);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        if (base.getShader() == null) base.setShader(new LinearGradient(0, 0, 0, h, colors[0], colors[colors.length - 1], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, base);
        float t = (SystemClock.uptimeMillis() % 600_000L) / 1000f; // seconds
        if (glyph == Glyph.SUN) sun(c, w, h, t);
        else if (glyph == Glyph.MOON) nightSky(c, w, h, t, true);
        else if (glyph == Glyph.CLOUD) { if (night) nightSky(c, w, h, t, false); clouds(c, w, h, t, 0x2effffff); }
        else if (glyph == Glyph.RAIN) { clouds(c, w, h, t, 0x26ffffff); rain(c, w, h, t); }
        else if (glyph == Glyph.FLASH) { clouds(c, w, h, t, 0x1fffffff); rain(c, w, h, t); lightning(c, w, h); }
        // keep moving only while someone can see it
        if (onTick != null) onTick.accept(animate ? t : 0f);
        // slow skies (sun, stars, clouds) need ~10 fps; rain and storms ~20 fps
        if (animate && glyph >= 0 && windowVisible && isShown()) postInvalidateDelayed(glyph == Glyph.RAIN || glyph == Glyph.FLASH ? 50 : 100);
    }

    private void sun(Canvas c, int w, int h, float t) {
        float cx = w * 0.86f, cy = h * 0.12f, r = h * 0.9f, pulse = 1f + 0.06f * (float) Math.sin(t * 1.2);
        if (glow == null || glowFor != h) { glowFor = h; glow = new RadialGradient(cx, cy, r, new int[]{0x40ffe9a0, 0x14ffd56a, 0x00ffd56a}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP); }
        c.save();
        c.scale(pulse, pulse, cx, cy); // the glow breathes without building a new gradient each frame
        p.setShader(glow);
        c.drawCircle(cx, cy, r, p);
        p.setShader(null);
        c.restore();
        // slowly turning rays
        p.setColor(0x0fffffff);
        p.setStrokeWidth(h * 0.05f);
        p.setStrokeCap(Paint.Cap.ROUND);
        float a0 = t * 0.12f;
        for (int k = 0; k < 10; k++) {
            double a = a0 + k * Math.PI / 5;
            c.drawLine(cx + (float) Math.cos(a) * r * 0.35f, cy + (float) Math.sin(a) * r * 0.35f,
                    cx + (float) Math.cos(a) * r * 0.8f, cy + (float) Math.sin(a) * r * 0.8f, p);
        }
    }

    private void nightSky(Canvas c, int w, int h, float t, boolean moonGlow) {
        p.setShader(null);
        for (int i = 0; i < stars.length; i += 3) {
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * 1.6 + stars[i + 2]));
            p.setColor(((int) (tw * 200) << 24) | 0xffffff);
            c.drawCircle(stars[i] * w, stars[i + 1] * h, Math.max(1.2f, h * 0.008f), p);
        }
        if (moonGlow) {
            float cx = w * 0.86f, cy = h * 0.15f, r = h * 0.7f, pulse = 1f + 0.05f * (float) Math.sin(t * 0.8);
            if (moon == null || glowFor != h) { glowFor = h; moon = new RadialGradient(cx, cy, r, new int[]{0x40dfe8ff, 0x14dfe8ff, 0x00dfe8ff}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP); }
            c.save();
            c.scale(pulse, pulse, cx, cy);
            p.setShader(moon);
            c.drawCircle(cx, cy, r, p);
            p.setShader(null);
            c.restore();
        }
    }

    private final android.graphics.Path cloud = new android.graphics.Path();
    private android.graphics.BlurMaskFilter soft;
    private int softFor = -1;

    /** Soft clouds: each one a single blurred shape (no overlapping see-through circles), kept clear of the text. */
    private void clouds(Canvas c, int w, int h, float t, int color) {
        p.setShader(null);
        p.setColor(color);
        if (soft == null || softFor != h) { softFor = h; soft = new android.graphics.BlurMaskFilter(h * 0.05f, android.graphics.BlurMaskFilter.Blur.NORMAL); }
        p.setMaskFilter(soft);
        // where each cloud sits when still: top-middle, upper right behind the weather, low left
        float[][] at = {{0.40f, 0.10f, 0.55f}, {0.74f, 0.30f, 0.42f}, {0.08f, 0.70f, 0.38f}};
        for (int k = 0; k < at.length; k++) {
            float size = h * at[k][2];
            float x = w * at[k][0], y = h * at[k][1];
            if (animate) x = ((x + t * (5f + k * 3f)) % (w + size * 2f)) - size;
            cloud.rewind();
            cloud.addCircle(x + size * 0.30f, y + size * 0.42f, size * 0.26f, android.graphics.Path.Direction.CW);
            cloud.addCircle(x + size * 0.62f, y + size * 0.30f, size * 0.34f, android.graphics.Path.Direction.CW);
            cloud.addCircle(x + size * 0.95f, y + size * 0.45f, size * 0.24f, android.graphics.Path.Direction.CW);
            cloud.addRoundRect(new android.graphics.RectF(x + size * 0.05f, y + size * 0.42f, x + size * 1.2f, y + size * 0.69f), size * 0.14f, size * 0.14f, android.graphics.Path.Direction.CW);
            cloud.setFillType(android.graphics.Path.FillType.WINDING); // overlaps fill once, so no darker seams
            c.drawPath(cloud, p);
        }
        p.setMaskFilter(null);
    }

    private void rain(Canvas c, int w, int h, float t) {
        p.setShader(null);
        p.setColor(0x59cfe3ff);
        p.setStrokeWidth(Math.max(1.5f, h * 0.012f));
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < drops.length; i += 3) {
            float speed = drops[i + 2];
            float y = ((drops[i + 1] + t * speed) % 1.2f) - 0.1f;
            float x = drops[i] * w - y * h * 0.15f;
            c.drawLine(x, y * h, x - h * 0.025f, y * h + h * 0.09f, p);
        }
    }

    private void lightning(Canvas c, int w, int h) {
        long now = SystemClock.uptimeMillis();
        if (now > flashAt + 6000 + rnd.nextInt(4000)) flashAt = now;
        long since = now - flashAt;
        if (since < 260) {
            int a = since < 80 ? 0x55 : since < 140 ? 0x10 : since < 200 ? 0x40 : 0x08;
            c.drawColor(a << 24 | 0xffffff);
        }
    }
}
