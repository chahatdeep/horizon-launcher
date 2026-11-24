package com.praiselab.horizon;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;

/** An app, shortcut or folder: round icon, one-line label, optional notification badge. */
final class IconView extends View {
    Item item;
    Bitmap icon;
    String label = "";
    boolean showLabel = true;
    private int badge;
    private String badgeMode = "count";
    private boolean folderHover;
    final int iconPx;
    private float scale = 1f;
    private ValueAnimator scaleAnim;
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ip = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint btp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private String shown;
    private int shownW = -1;

    IconView(Context c, int iconPx) {
        super(c);
        this.iconPx = iconPx;
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTypeface(U.REGULAR);
        tp.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13.5f, c.getResources().getDisplayMetrics()));
        btp.setTextAlign(Paint.Align.CENTER);
        btp.setTypeface(U.MEDIUM);
        btp.setColor(0xffffffff);
        btp.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, c.getResources().getDisplayMetrics()));
        setClickable(true);
        setLongClickable(true);
        setFocusable(true);
    }

    void bind(Item item, Bitmap icon, String label) {
        this.item = item;
        this.icon = icon;
        this.label = label == null ? "" : label;
        setTag(item);
        setContentDescription(this.label);
        shownW = -1;
        invalidate();
    }

    /** White text with a soft shadow on wallpaper, or a plain palette color inside panels. */
    void setLabelColor(int color, boolean shadow) {
        tp.setColor(color);
        if (shadow) tp.setShadowLayer(U.dp(3), 0, U.dp(0.5f), 0x73000000); else tp.clearShadowLayer();
        invalidate();
    }

    void setBadge(int n, String mode) {
        if (n == badge && mode.equals(badgeMode)) return;
        badge = n;
        badgeMode = mode;
        invalidate();
    }

    void setFolderHover(boolean on) {
        if (folderHover != on) { folderHover = on; invalidate(); }
    }

    int contentHeight() {
        return iconPx + (showLabel ? U.dp(6) + Math.round(tp.descent() - tp.ascent()) : 0);
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        int height = MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED ? contentHeight() + U.dp(20) : MeasureSpec.getSize(h);
        setMeasuredDimension(width, height);
    }

    private float iconTop() { return (getHeight() - contentHeight()) / 2f; }

    /** Where the icon itself sits, relative to an ancestor (for drag and launch animations). */
    Rect iconRectIn(View ancestor) {
        Rect r = U.rectIn(this, ancestor);
        int left = r.left + (getWidth() - iconPx) / 2, top = r.top + Math.round(iconTop());
        return new Rect(left, top, left + iconPx, top + iconPx);
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, top = iconTop(), cy = top + iconPx / 2f;
        if (folderHover) {
            bp.setColor(0x66ffffff);
            c.drawCircle(cx, cy, iconPx * 0.66f, bp);
        }
        c.save();
        c.scale(scale, scale, cx, cy);
        if (icon != null) {
            dst.set(cx - iconPx / 2f, top, cx + iconPx / 2f, top + iconPx);
            c.drawBitmap(icon, null, dst, ip);
        }
        drawBadge(c, cx, top);
        c.restore();
        if (showLabel && getWidth() > 0) {
            if (shownW != getWidth()) {
                shown = TextUtils.ellipsize(label, tp, getWidth() - U.dp(6), TextUtils.TruncateAt.END).toString();
                shownW = getWidth();
            }
            c.drawText(shown, cx, top + iconPx + U.dp(6) - tp.ascent(), tp);
        }
    }

    private void drawBadge(Canvas c, float cx, float top) {
        if (badge <= 0 || "off".equals(badgeMode)) return;
        float bx = cx + iconPx * 0.36f, by = top + iconPx * 0.12f;
        bp.setColor(0xffea4335);
        if ("dot".equals(badgeMode)) {
            c.drawCircle(bx, by, U.dp(5.5f), bp);
            return;
        }
        String t = badge > 99 ? "99+" : String.valueOf(badge);
        float h = U.dp(19), w = Math.max(h, btp.measureText(t) + U.dp(10));
        RectF r = new RectF(bx - w / 2f, by - h / 2f, bx + w / 2f, by + h / 2f);
        c.drawRoundRect(r, h / 2f, h / 2f, bp);
        c.drawText(t, bx, by - (btp.ascent() + btp.descent()) / 2f, btp);
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        if (scaleAnim != null) scaleAnim.cancel();
        scaleAnim = ValueAnimator.ofFloat(scale, pressed ? 0.9f : 1f).setDuration(pressed ? 120 : 220);
        scaleAnim.addUpdateListener(a -> { scale = (float) a.getAnimatedValue(); invalidate(); });
        scaleAnim.start();
    }
}
