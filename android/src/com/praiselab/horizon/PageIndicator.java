package com.praiselab.horizon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Page dots; the Now feed shows as a ring so it reads as a different kind of page. */
final class PageIndicator extends View {
    private int count;
    private boolean feed;
    private float pos;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    PageIndicator(Context c) { super(c); }

    void set(int count, boolean feed) { this.count = count; this.feed = feed; invalidate(); }
    void setPos(float pos) { this.pos = pos; invalidate(); }
    void setColor(int c) { p.setColor(c); invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        if (count <= 1) return;
        float gap = U.dp(17), r = U.dp(3.2f), total = (count - 1) * gap, x0 = getWidth() / 2f - total / 2f, cy = getHeight() / 2f;
        for (int i = 0; i < count; i++) {
            float near = 1f - Math.min(1f, Math.abs(pos - i));
            p.setAlpha(Math.round(255 * (0.4f + 0.55f * near)));
            float cx = x0 + i * gap, rr = r;
            if (near > 0f && !(feed && i == 0)) {
                // the current page stretches into a pill, like iOS and One UI
                float half = r + U.dp(4) * near;
                c.drawRoundRect(cx - half, cy - r, cx + half, cy + r, r, r, p);
                continue;
            }
            if (feed && i == 0) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(U.dp(1.5f));
                c.drawCircle(cx, cy, rr, p);
                p.setStyle(Paint.Style.FILL);
            } else {
                c.drawCircle(cx, cy, rr, p);
            }
        }
    }
}
