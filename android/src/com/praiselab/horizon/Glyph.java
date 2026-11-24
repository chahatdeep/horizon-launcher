package com.praiselab.horizon;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Line icons on a 24-unit grid, drawn in code so the launcher ships no image assets. */
final class Glyph extends Drawable {
    static final int SEARCH = 0, MIC = 1, CLOSE = 2, INFO = 3, TRASH = 4, REMOVE = 5, WIDGETS = 6,
            WALLPAPER = 7, SETTINGS = 8, PIN = 9, HIDE = 10, APPS = 11, HOME = 12, PLUS = 13, CHECK = 14,
            PLAY = 15, PAUSE = 16, NEXT = 17, PREV = 18, MUSIC = 19, UP = 20, DOWN = 21, RESIZE = 22,
            WIFI = 23, BLUETOOTH = 24, FLASH = 25, MOON = 26, LOCATION = 27, BELL = 28, SUN = 29, PALETTE = 30, LENS = 31, CLOUD = 32, RAIN = 33;

    private final int type;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    Glyph(int type, int color) {
        this.type = type;
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override public int getIntrinsicWidth() { return U.dp(24); }
    @Override public int getIntrinsicHeight() { return U.dp(24); }

    private void poly(Canvas c, boolean close, float... xy) {
        path.reset();
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) path.lineTo(xy[i], xy[i + 1]);
        if (close) path.close();
        c.drawPath(path, p);
    }

    @Override
    public void draw(Canvas c) {
        float s = getBounds().width() / 24f;
        c.save();
        c.translate(getBounds().left, getBounds().top);
        c.scale(s, s);
        switch (type) {
            case SEARCH:
                c.drawCircle(11, 11, 6.5f, p);
                c.drawLine(15.7f, 15.7f, 20, 20, p);
                break;
            case MIC:
                c.drawRoundRect(new RectF(9, 3.5f, 15, 14), 3, 3, p);
                c.drawArc(new RectF(6, 5, 18, 17), 0, 180, false, p);
                c.drawLine(12, 17, 12, 20.5f, p);
                break;
            case CLOSE:
                c.drawLine(6, 6, 18, 18, p);
                c.drawLine(18, 6, 6, 18, p);
                break;
            case INFO:
                c.drawCircle(12, 12, 8.5f, p);
                c.drawLine(12, 11, 12, 16.5f, p);
                c.drawPoint(12, 7.8f, p);
                break;
            case TRASH:
                c.drawLine(5, 7, 19, 7, p);
                poly(c, false, 10, 7, 10, 4.8f, 14, 4.8f, 14, 7);
                poly(c, false, 7, 7, 8, 19.5f, 16, 19.5f, 17, 7);
                break;
            case REMOVE:
                c.drawCircle(12, 12, 8.5f, p);
                c.drawLine(8, 12, 16, 12, p);
                break;
            case WIDGETS:
                c.drawRoundRect(new RectF(4, 4, 10.5f, 10.5f), 2, 2, p);
                c.drawRoundRect(new RectF(13.5f, 4, 20, 10.5f), 2, 2, p);
                c.drawRoundRect(new RectF(4, 13.5f, 10.5f, 20), 2, 2, p);
                c.drawRoundRect(new RectF(13.5f, 13.5f, 20, 20), 2, 2, p);
                break;
            case WALLPAPER:
                c.drawRoundRect(new RectF(3.5f, 5, 20.5f, 19), 3, 3, p);
                poly(c, false, 3.8f, 16.5f, 8.5f, 11.8f, 12.5f, 15.8f, 15.3f, 13, 20.2f, 17.9f);
                c.drawCircle(15.6f, 9.4f, 1.5f, p);
                break;
            case SETTINGS:
                c.drawLine(4, 7, 12.7f, 7, p);
                c.drawLine(17.3f, 7, 20, 7, p);
                c.drawLine(4, 17, 6.7f, 17, p);
                c.drawLine(11.3f, 17, 20, 17, p);
                c.drawCircle(15, 7, 2.3f, p);
                c.drawCircle(9, 17, 2.3f, p);
                break;
            case PIN:
                poly(c, true, 12, 3.5f, 14.6f, 8.8f, 20.5f, 9.7f, 16.2f, 13.8f, 17.2f, 19.6f,
                        12, 16.9f, 6.8f, 19.6f, 7.8f, 13.8f, 3.5f, 9.7f, 9.4f, 8.8f);
                break;
            case HIDE:
                c.drawOval(new RectF(3, 7, 21, 17), p);
                c.drawCircle(12, 12, 2.6f, p);
                c.drawLine(4, 20, 20, 4, p);
                break;
            case APPS:
                c.drawCircle(7, 7, 2.6f, p);
                c.drawCircle(17, 7, 2.6f, p);
                c.drawCircle(7, 17, 2.6f, p);
                c.drawCircle(17, 17, 2.6f, p);
                break;
            case HOME:
                poly(c, true, 4, 11, 12, 4.5f, 20, 11, 20, 20, 4, 20);
                break;
            case PLUS:
                c.drawLine(12, 5, 12, 19, p);
                c.drawLine(5, 12, 19, 12, p);
                break;
            case CHECK:
                poly(c, false, 5, 12.5f, 9.5f, 17, 19, 7.5f);
                break;
            case PLAY:
                fill(c, true);
                poly(c, true, 8, 5.5f, 19, 12, 8, 18.5f);
                fill(c, false);
                break;
            case PAUSE:
                fill(c, true);
                c.drawRoundRect(new RectF(6.5f, 5, 10, 19), 1.2f, 1.2f, p);
                c.drawRoundRect(new RectF(14, 5, 17.5f, 19), 1.2f, 1.2f, p);
                fill(c, false);
                break;
            case NEXT:
                fill(c, true);
                poly(c, true, 5, 6, 13, 12, 5, 18);
                poly(c, true, 12, 6, 20, 12, 12, 18);
                fill(c, false);
                break;
            case PREV:
                fill(c, true);
                poly(c, true, 19, 6, 11, 12, 19, 18);
                poly(c, true, 12, 6, 4, 12, 12, 18);
                fill(c, false);
                break;
            case MUSIC:
                poly(c, false, 9, 17.5f, 9, 6.2f, 19, 4.2f, 19, 15.5f);
                c.drawCircle(6.6f, 17.6f, 2.4f, p);
                c.drawCircle(16.6f, 15.6f, 2.4f, p);
                break;
            case UP:
                poly(c, false, 6, 15, 12, 9, 18, 15);
                break;
            case DOWN:
                poly(c, false, 6, 9, 12, 15, 18, 9);
                break;
            case WIFI:
                c.drawArc(new RectF(2.5f, 5, 21.5f, 24), 225, 90, false, p);
                c.drawArc(new RectF(6, 8.5f, 18, 20.5f), 225, 90, false, p);
                c.drawArc(new RectF(9.5f, 12, 14.5f, 17), 225, 90, false, p);
                c.drawPoint(12, 19, p);
                break;
            case BLUETOOTH:
                poly(c, false, 7, 7.5f, 16.5f, 16, 12, 20, 12, 4, 16.5f, 8, 7, 16.5f);
                break;
            case FLASH:
                poly(c, true, 13, 3, 6, 13.5f, 11.5f, 13.5f, 10.5f, 21, 18, 10.5f, 12.5f, 10.5f);
                break;
            case MOON:
                c.drawArc(new RectF(4, 4, 20, 20), 60, 260, false, p);
                c.drawArc(new RectF(9.5f, 1.5f, 22.5f, 14.5f), 120, 120, false, p);
                break;
            case LOCATION:
                path.reset();
                path.moveTo(12, 21);
                path.cubicTo(12, 21, 5.5f, 15.4f, 5.5f, 10);
                path.cubicTo(5.5f, 6.4f, 8.4f, 3.5f, 12, 3.5f);
                path.cubicTo(15.6f, 3.5f, 18.5f, 6.4f, 18.5f, 10);
                path.cubicTo(18.5f, 15.4f, 12, 21, 12, 21);
                c.drawPath(path, p);
                c.drawCircle(12, 10, 2.3f, p);
                break;
            case BELL:
                path.reset();
                path.moveTo(6, 16.5f);
                path.lineTo(6, 11);
                path.cubicTo(6, 7.5f, 8.7f, 5, 12, 5);
                path.cubicTo(15.3f, 5, 18, 7.5f, 18, 11);
                path.lineTo(18, 16.5f);
                path.lineTo(19.5f, 18);
                path.lineTo(4.5f, 18);
                path.close();
                c.drawPath(path, p);
                c.drawLine(10.5f, 20.5f, 13.5f, 20.5f, p);
                break;
            case SUN:
                c.drawCircle(12, 12, 4, p);
                for (int k = 0; k < 8; k++) {
                    double a = Math.PI / 4 * k;
                    c.drawLine(12 + 6.5f * (float) Math.cos(a), 12 + 6.5f * (float) Math.sin(a),
                            12 + 8.5f * (float) Math.cos(a), 12 + 8.5f * (float) Math.sin(a), p);
                }
                break;
            case PALETTE:
                c.drawCircle(12, 12, 8.5f, p);
                c.drawCircle(8.5f, 10, 1.2f, p);
                c.drawCircle(12, 7.5f, 1.2f, p);
                c.drawCircle(15.5f, 10, 1.2f, p);
                c.drawCircle(14, 15.5f, 1.8f, p);
                break;
            case CLOUD:
            case RAIN:
                path.reset();
                path.moveTo(7.5f, 18);
                path.lineTo(17, 18);
                path.cubicTo(19.5f, 18, 21, 16.4f, 21, 14.4f);
                path.cubicTo(21, 12.4f, 19.4f, 10.9f, 17.6f, 10.9f);
                path.cubicTo(17, 8.1f, 14.8f, 6, 12, 6);
                path.cubicTo(9, 6, 6.6f, 8.3f, 6.4f, 11.2f);
                path.cubicTo(4.5f, 11.6f, 3, 13.1f, 3, 14.9f);
                path.cubicTo(3, 16.6f, 4.7f, 18, 7.5f, 18);
                path.close();
                if (type == RAIN) {
                    c.save();
                    c.translate(0, -3);
                    c.drawPath(path, p);
                    c.restore();
                    c.drawLine(8.5f, 18, 7.5f, 21, p);
                    c.drawLine(12.5f, 18, 11.5f, 21, p);
                    c.drawLine(16.5f, 18, 15.5f, 21, p);
                } else c.drawPath(path, p);
                break;
            case LENS:
                poly(c, false, 4, 9, 4, 6, 6, 4, 9, 4);
                poly(c, false, 15, 4, 18, 4, 20, 6, 20, 9);
                poly(c, false, 20, 15, 20, 18, 18, 20, 15, 20);
                poly(c, false, 9, 20, 6, 20, 4, 18, 4, 15);
                c.drawCircle(12, 12, 3.2f, p);
                break;
            case RESIZE:
                c.drawRoundRect(new RectF(4, 4, 20, 20), 3, 3, p);
                poly(c, false, 13, 9, 15, 9, 15, 11);
                poly(c, false, 11, 15, 9, 15, 9, 13);
                break;
        }
        c.restore();
    }

    private void fill(Canvas c, boolean on) {
        p.setStyle(on ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);
    }

    @Override public void setAlpha(int a) { p.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
