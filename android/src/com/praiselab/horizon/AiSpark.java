package com.praiselab.horizon;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** The AI sparkle: a four-point star with a blue-to-violet gradient, as on One UI's Galaxy AI buttons. */
final class AiSpark extends Drawable {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path star = new Path();

    @Override public int getIntrinsicWidth() { return U.dp(24); }
    @Override public int getIntrinsicHeight() { return U.dp(24); }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = b.width() / 24f;
        p.setShader(new LinearGradient(b.left, b.bottom, b.right, b.top,
                new int[]{0xff3d7bff, 0xff8a5cff, 0xffd36bff}, null, Shader.TileMode.CLAMP));
        c.save();
        c.translate(b.left, b.top);
        c.scale(s, s);
        star.reset();
        // large star
        star.moveTo(10, 3);
        star.cubicTo(10.8f, 7.6f, 12.4f, 9.2f, 17, 10);
        star.cubicTo(12.4f, 10.8f, 10.8f, 12.4f, 10, 17);
        star.cubicTo(9.2f, 12.4f, 7.6f, 10.8f, 3, 10);
        star.cubicTo(7.6f, 9.2f, 9.2f, 7.6f, 10, 3);
        star.close();
        // small star
        star.moveTo(17.5f, 13.5f);
        star.cubicTo(17.9f, 15.6f, 18.4f, 16.1f, 20.5f, 16.5f);
        star.cubicTo(18.4f, 16.9f, 17.9f, 17.4f, 17.5f, 19.5f);
        star.cubicTo(17.1f, 17.4f, 16.6f, 16.9f, 14.5f, 16.5f);
        star.cubicTo(16.6f, 16.1f, 17.1f, 15.6f, 17.5f, 13.5f);
        star.close();
        c.drawPath(star, p);
        c.restore();
    }

    @Override public void setAlpha(int a) { p.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
