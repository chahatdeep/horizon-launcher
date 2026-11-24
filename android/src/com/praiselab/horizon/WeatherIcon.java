package com.praiselab.horizon;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Modern multicolour weather symbols (in the spirit of Apple's): gradient sun with a soft glow, a pearl moon,
 * layered clouds with depth, blue rain drops, a golden bolt, snowflakes and fog bands.
 */
final class WeatherIcon extends Drawable {
    /** Extra conditions beyond the basic glyphs (sun, moon, cloud, rain, flash). */
    static final int PARTLY_DAY = 100, PARTLY_NIGHT = 101, SNOW = 102, FOG = 103;

    /** The base condition, for the sky behind the card. */
    static int base(int g) {
        if (g == PARTLY_DAY) return Glyph.SUN;
        if (g == PARTLY_NIGHT) return Glyph.MOON;
        if (g == SNOW || g == FOG) return Glyph.CLOUD;
        return g;
    }

    private final int kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    WeatherIcon(int glyph) { kind = glyph; }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()), x = b.left + (b.width() - s) / 2f, y = b.top + (b.height() - s) / 2f;
        switch (kind) {
            case Glyph.SUN: sun(c, x + s * 0.5f, y + s * 0.5f, s * 0.5f, true); break;
            case Glyph.MOON: moon(c, x + s * 0.5f, y + s * 0.5f, s * 0.42f, true); break;
            case PARTLY_DAY: sun(c, x + s * 0.62f, y + s * 0.36f, s * 0.36f, false); cloud(c, x, y + s * 0.18f, s * 0.92f, false); break;
            case PARTLY_NIGHT: moon(c, x + s * 0.64f, y + s * 0.34f, s * 0.3f, false); cloud(c, x, y + s * 0.18f, s * 0.92f, false); break;
            case Glyph.RAIN: cloud(c, x, y - s * 0.06f, s, false); drops(c, x, y, s); break;
            case Glyph.FLASH: cloud(c, x, y - s * 0.06f, s, true); bolt(c, x, y, s); break;
            case SNOW: cloud(c, x, y - s * 0.06f, s, false); flakes(c, x, y, s); break;
            case FOG: cloud(c, x, y - s * 0.12f, s, false); fog(c, x, y, s); break;
            default: cloud(c, x, y, s, false);
        }
    }

    private void sun(Canvas c, float cx, float cy, float r, boolean rays) {
        p.setStyle(Paint.Style.FILL);
        // soft glow
        p.setColor(0xffffffff);
        p.setShader(new RadialGradient(cx, cy, r, new int[]{0x66ffd84d, 0x00ffd84d}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
        if (rays) {
            p.setShader(null);
            p.setColor(0xffffc53d);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(r * 0.12f);
            p.setStyle(Paint.Style.STROKE);
            for (int k = 0; k < 8; k++) {
                double a = Math.PI / 4 * k;
                c.drawLine(cx + (float) Math.cos(a) * r * 0.66f, cy + (float) Math.sin(a) * r * 0.66f,
                        cx + (float) Math.cos(a) * r * 0.86f, cy + (float) Math.sin(a) * r * 0.86f, p);
            }
            p.setStyle(Paint.Style.FILL);
        }
        float d = rays ? r * 0.46f : r * 0.62f;
        p.setShader(new LinearGradient(cx - d, cy - d, cx + d, cy + d, 0xffffe680, 0xffffa31a, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, d, p);
        p.setShader(null);
    }

    private void moon(Canvas c, float cx, float cy, float r, boolean stars) {
        path.reset();
        path.addCircle(cx, cy, r, Path.Direction.CW);
        Path cut = new Path();
        cut.addCircle(cx + r * 0.62f, cy - r * 0.42f, r * 0.82f, Path.Direction.CW);
        path.op(cut, Path.Op.DIFFERENCE);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xffffffff);
        p.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xfffffbe6, 0xffe6d38f, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        p.setShader(null);
        if (stars) {
            p.setColor(0xfffff3c4);
            star(c, cx + r * 0.75f, cy - r * 0.55f, r * 0.16f);
            star(c, cx + r * 0.35f, cy + r * 0.05f, r * 0.1f);
        }
    }

    private void star(Canvas c, float cx, float cy, float r) {
        path.reset();
        path.moveTo(cx, cy - r);
        path.quadTo(cx, cy, cx + r, cy);
        path.quadTo(cx, cy, cx, cy + r);
        path.quadTo(cx, cy, cx - r, cy);
        path.quadTo(cx, cy, cx, cy - r);
        c.drawPath(path, p);
    }

    /** A rounded, layered cloud with a light-to-shade gradient and a soft shadow underneath. */
    private void cloud(Canvas c, float x, float y, float s, boolean dark) {
        path.reset();
        path.addCircle(x + s * 0.34f, y + s * 0.56f, s * 0.17f, Path.Direction.CW);
        path.addCircle(x + s * 0.53f, y + s * 0.45f, s * 0.23f, Path.Direction.CW);
        path.addCircle(x + s * 0.72f, y + s * 0.57f, s * 0.15f, Path.Direction.CW);
        path.addRoundRect(new RectF(x + s * 0.17f, y + s * 0.56f, x + s * 0.87f, y + s * 0.73f), s * 0.085f, s * 0.085f, Path.Direction.CW);
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(0x26000000);
        c.save();
        c.translate(0, s * 0.03f);
        c.drawPath(path, p);
        c.restore();
        p.setColor(0xffffffff); // the shadow's 15% alpha would otherwise fade the cloud itself
        int top = dark ? 0xffaab4c4 : 0xffffffff, bottom = dark ? 0xff6b778a : 0xffd6e2f0;
        p.setShader(new LinearGradient(0, y + s * 0.22f, 0, y + s * 0.73f, top, bottom, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        p.setShader(null);
    }

    private void drops(Canvas c, float x, float y, float s) {
        p.setStyle(Paint.Style.FILL);
        for (int k = 0; k < 3; k++) {
            float cx = x + s * (0.32f + k * 0.18f), cy = y + s * 0.84f - (k % 2) * s * 0.04f, r = s * 0.055f;
            path.reset();
            path.moveTo(cx, cy - r * 2.2f);
            path.cubicTo(cx + r * 0.2f, cy - r * 1.2f, cx + r, cy - r * 0.4f, cx + r, cy);
            path.arcTo(new RectF(cx - r, cy - r, cx + r, cy + r), 0, 180);
            path.cubicTo(cx - r, cy - r * 0.4f, cx - r * 0.2f, cy - r * 1.2f, cx, cy - r * 2.2f);
            p.setColor(0xffffffff);
        p.setShader(new LinearGradient(0, cy - r * 2.2f, 0, cy + r, 0xff7cc4ff, 0xff2f80ed, Shader.TileMode.CLAMP));
            c.drawPath(path, p);
        }
        p.setShader(null);
    }

    private void bolt(Canvas c, float x, float y, float s) {
        path.reset();
        path.moveTo(x + s * 0.56f, y + s * 0.6f);
        path.lineTo(x + s * 0.42f, y + s * 0.8f);
        path.lineTo(x + s * 0.52f, y + s * 0.8f);
        path.lineTo(x + s * 0.45f, y + s * 0.98f);
        path.lineTo(x + s * 0.64f, y + s * 0.74f);
        path.lineTo(x + s * 0.54f, y + s * 0.74f);
        path.lineTo(x + s * 0.62f, y + s * 0.6f);
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xffffffff);
        p.setShader(new LinearGradient(0, y + s * 0.6f, 0, y + s * 0.98f, 0xffffe066, 0xffff9f0a, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        p.setShader(null);
    }

    private void flakes(Canvas c, float x, float y, float s) {
        p.setShader(null);
        p.setColor(0xffeaf4ff);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(s * 0.03f);
        for (int k = 0; k < 3; k++) {
            float cx = x + s * (0.32f + k * 0.18f), cy = y + s * 0.84f - (k % 2) * s * 0.05f, r = s * 0.06f;
            for (int a = 0; a < 3; a++) {
                double ang = Math.PI / 3 * a;
                c.drawLine(cx - (float) Math.cos(ang) * r, cy - (float) Math.sin(ang) * r, cx + (float) Math.cos(ang) * r, cy + (float) Math.sin(ang) * r, p);
            }
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void fog(Canvas c, float x, float y, float s) {
        p.setShader(null);
        p.setColor(0xccdbe4ef);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(s * 0.055f);
        c.drawLine(x + s * 0.2f, y + s * 0.78f, x + s * 0.8f, y + s * 0.78f, p);
        c.drawLine(x + s * 0.3f, y + s * 0.9f, x + s * 0.72f, y + s * 0.9f, p);
        p.setStyle(Paint.Style.FILL);
    }

    @Override public void setAlpha(int a) { p.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
