package com.praiselab.horizon;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * Continuous-corner ("squircle") shape in the Apple style: the curve starts earlier and eases into the
 * straight edge, instead of a circular arc meeting it abruptly. Used for every surface in Horizon.
 */
final class Sq extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float radius;
    private final Path path = new Path();
    private final RectF box = new RectF();

    Sq(int color, float radiusPx) {
        fill.setColor(color);
        stroke.setStyle(Paint.Style.STROKE);
        radius = radiusPx;
    }

    void setColor(int c) { fill.setColor(c); invalidateSelf(); }

    void setStroke(int widthPx, int color) {
        stroke.setStrokeWidth(widthPx);
        stroke.setColor(color);
        invalidateSelf();
    }

    void setCornerRadius(float r) { radius = r; invalidateSelf(); }

    /** Continuous-corner path; radius is the visual corner size (like iOS cornerRadius with .continuous). */
    static Path path(Path p, RectF b, float r) {
        p.reset();
        float half = Math.min(b.width(), b.height()) / 2f;
        r = Math.max(0, Math.min(r, half));
        float s = Math.min(r * 1.28f, half); // where the curve begins along each edge
        float k = s * 0.36f;                 // control points hug the corner for a gradual turn
        p.moveTo(b.left + s, b.top);
        p.lineTo(b.right - s, b.top);
        p.cubicTo(b.right - k, b.top, b.right, b.top + k, b.right, b.top + s);
        p.lineTo(b.right, b.bottom - s);
        p.cubicTo(b.right, b.bottom - k, b.right - k, b.bottom, b.right - s, b.bottom);
        p.lineTo(b.left + s, b.bottom);
        p.cubicTo(b.left + k, b.bottom, b.left, b.bottom - k, b.left, b.bottom - s);
        p.lineTo(b.left, b.top + s);
        p.cubicTo(b.left, b.top + k, b.left + k, b.top, b.left + s, b.top);
        p.close();
        return p;
    }

    static Path path(RectF b, float r) { return path(new Path(), b, r); }

    /** Clips a view to the same continuous corners (widgets, artwork, cards). */
    static void clip(View v, float radiusPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setPath(path(new RectF(0, 0, view.getWidth(), view.getHeight()), radiusPx));
            }
        });
        v.setClipToOutline(true);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float inset = stroke.getStrokeWidth() > 0 ? stroke.getStrokeWidth() / 2f : 0;
        box.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        path(path, box, radius);
        if (glassy && fill.getAlpha() > 0 && fill.getAlpha() < 250) {
            // Liquid Glass: the blurred wallpaper behind, a whisper of tint, light-catching edges
            android.view.View host = getCallback() instanceof android.view.View ? (android.view.View) getCallback() : null;
            Glass.track(host);
            int tint = Glass.ready() && host != null ? U.alpha(fill.getColor() | 0xff000000, 0.28f) : fill.getColor();
            Glass.body(c, path, box, host, tint);
            Glass.rim(c, path, box);
            return;
        }
        c.drawPath(path, fill);
        if (stroke.getStrokeWidth() > 0 && stroke.getColor() != 0) c.drawPath(path, stroke);
        if (glassy && fill.getAlpha() > 0) glass(c, box);
    }

    /** visionOS glass: set by the Spatial look. Every surface gets a soft top sheen and a light-catching edge. */
    static boolean glassy;
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float shaderTop = -1, shaderBottom = -1;

    private void glass(Canvas c, RectF b) {
        if (b.height() < U.dp(12)) return;
        if (shaderTop != b.top || shaderBottom != b.bottom) {
            shaderTop = b.top;
            shaderBottom = b.bottom;
            float h = Math.min(b.height(), U.dp(90));
            sheen.setShader(new android.graphics.LinearGradient(0, b.top, 0, b.top + h, 0x1fffffff, 0x00ffffff, android.graphics.Shader.TileMode.CLAMP));
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(U.dp(1));
            edge.setShader(new android.graphics.LinearGradient(0, b.top, 0, b.bottom, 0x66ffffff, 0x14ffffff, android.graphics.Shader.TileMode.CLAMP));
        }
        c.drawPath(path, sheen);
        c.drawPath(path, edge);
    }

    @Override
    public void getOutline(Outline o) {
        Rect b = getBounds();
        o.setPath(path(new RectF(b), radius));
    }

    @Override public void setAlpha(int a) { fill.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
