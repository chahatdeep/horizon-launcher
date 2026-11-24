package com.praiselab.horizon;

import android.view.Choreographer;

/**
 * A critically-damped-ish spring, iOS style: it starts with the finger's velocity and settles naturally,
 * instead of a fixed-length eased animation. One small physics step per frame.
 */
final class Spring implements Choreographer.FrameCallback {
    interface Listener { void onValue(float v); }

    private final float stiffness, damping;
    private float x, v, target;
    private long last;
    private boolean running;
    private Listener listener;
    private Runnable end;

    /** dampingRatio 1 = no bounce; 0.8 = a hint of overshoot. */
    Spring(float stiffness, float dampingRatio) {
        this.stiffness = stiffness;
        this.damping = 2f * dampingRatio * (float) Math.sqrt(stiffness);
    }

    /** Animates from 'from' toward 'to', starting at 'velocity' (units per second). */
    void start(float from, float to, float velocity, Listener l, Runnable onEnd) {
        x = from; target = to; v = velocity; listener = l; end = onEnd;
        last = 0;
        if (!running) { running = true; Choreographer.getInstance().postFrameCallback(this); }
    }

    void cancel() {
        if (running) { running = false; Choreographer.getInstance().removeFrameCallback(this); }
    }

    boolean running() { return running; }

    @Override
    public void doFrame(long now) {
        if (!running) return;
        if (last == 0) last = now - 16_000_000L;
        float dt = Math.min(0.032f, (now - last) / 1e9f);
        last = now;
        // a few sub-steps keep stiff springs stable
        for (int i = 0; i < 4; i++) {
            float h = dt / 4f;
            float a = -stiffness * (x - target) - damping * v;
            v += a * h;
            x += v * h;
        }
        float span = Math.max(1e-3f, Math.abs(target) + 1f);
        if (Math.abs(x - target) < span * 0.0005f && Math.abs(v) < span * 0.01f) {
            x = target;
            running = false;
            listener.onValue(x);
            if (end != null) end.run();
            return;
        }
        listener.onValue(x);
        Choreographer.getInstance().postFrameCallback(this);
    }
}
