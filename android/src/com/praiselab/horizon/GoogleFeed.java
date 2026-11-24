package com.praiselab.horizon;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Process;
import android.view.WindowManager;

/**
 * The real Google feed (Discover), shown by the Google app over the launcher when you swipe right on
 * the first Home page. This speaks the same launcher-overlay protocol Pixel Launcher uses.
 */
final class GoogleFeed {
    interface Listener {
        /** Called on the main thread when the feed becomes usable (or stops being usable). */
        void onFeedAvailable(boolean available);
        /** How far the feed is open, 0..1. */
        void onFeedScroll(float progress);
    }

    static final String GSA = "com.google.android.googlequicksearchbox";
    private static final String OVERLAY = "com.google.android.libraries.launcherclient.ILauncherOverlay";
    private static final String CALLBACK = "com.google.android.libraries.launcherclient.ILauncherOverlayCallback";
    // ILauncherOverlay transaction codes
    private static final int START_SCROLL = 1, ON_SCROLL = 2, END_SCROLL = 3, WINDOW_DETACHED = 5,
            CLOSE_OVERLAY = 6, ON_PAUSE = 7, ON_RESUME = 8, OPEN_OVERLAY = 9, WINDOW_ATTACHED2 = 14, SET_ACTIVITY_STATE = 16;
    private static final int STATE_STARTED = 1, STATE_RESUMED = 2;

    private final Activity activity;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private IBinder overlay;
    private boolean bound, attached, available, scrolling;
    private int state;

    private final Binder callback = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == INTERFACE_TRANSACTION) { if (reply != null) reply.writeString(CALLBACK); return true; }
            try {
                data.enforceInterface(CALLBACK);
                if (code == 1) {
                    float p = data.readFloat();
                    main.post(() -> listener.onFeedScroll(p));
                    return true;
                }
                if (code == 2) {
                    int status = data.readInt();
                    android.util.Log.i("HorizonFeed", "overlay status " + status);
                    boolean ok = (status & 1) != 0;
                    main.post(() -> setAvailable(ok));
                    return true;
                }
            } catch (Exception ignored) { }
            return false;
        }
    };

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            overlay = b;
            attach();
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            overlay = null;
            attached = false;
            setAvailable(false);
        }
        /** The Google app turned us down: no feed, so Home falls back to Horizon's own page. */
        @Override public void onNullBinding(ComponentName n) {
            android.util.Log.w("HorizonFeed", "null binding");
            overlay = null;
            attached = false;
            setAvailable(false);
        }
        /** The Google app was updated or killed for good: Android won't reconnect this binding, so start a new one. */
        @Override public void onBindingDied(ComponentName n) {
            android.util.Log.i("HorizonFeed", "binding died, reconnecting");
            overlay = null;
            attached = false;
            setAvailable(false);
            if (bound) { try { activity.unbindService(conn); } catch (Exception ignored) { } bound = false; }
            main.postDelayed(GoogleFeed.this::connect, 1500);
        }
    };

    GoogleFeed(Activity a, Listener l) {
        activity = a;
        listener = l;
    }

    boolean available() { return available && overlay != null && attached; }

    private void setAvailable(boolean ok) {
        if (ok == available) return;
        available = ok;
        listener.onFeedAvailable(ok);
    }

    void connect() {
        if (bound) return;
        Uri data = Uri.parse("app://" + activity.getPackageName() + ":" + Process.myUid()).buildUpon()
                .appendQueryParameter("v", "7").appendQueryParameter("cv", "9").build();
        Intent i = new Intent("com.android.launcher3.WINDOW_OVERLAY").setPackage(GSA).setData(data);
        try { bound = activity.bindService(i, conn, Context.BIND_AUTO_CREATE | Context.BIND_ADJUST_WITH_ACTIVITY); }
        catch (Exception e) { bound = false; }
    }

    /** Call once the launcher window exists (and again if it is re-created). */
    void attach() {
        if (overlay == null || activity.getWindow() == null || activity.getWindow().getDecorView().getWindowToken() == null) return;
        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        Bundle b = new Bundle();
        b.putParcelable("layout_params", lp);
        // the phone's own display settings, not Horizon's text/content scaling, so the feed lays out like on Pixel
        android.content.res.Configuration cfg = new android.content.res.Configuration(activity.getApplicationContext().getResources().getConfiguration());
        b.putParcelable("configuration", cfg);
        b.putInt("client_options", 1);
        Parcel d = Parcel.obtain();
        try {
            d.writeInterfaceToken(OVERLAY);
            d.writeInt(1);
            b.writeToParcel(d, 0);
            d.writeStrongBinder(callback);
            overlay.transact(Binder.FIRST_CALL_TRANSACTION + WINDOW_ATTACHED2 - 1, d, null, IBinder.FLAG_ONEWAY);
            attached = true;
            android.util.Log.i("HorizonFeed", "attached");
        } catch (Exception e) {
            attached = false;
        } finally { d.recycle(); }
        sendState();
    }

    void onStart() { state |= STATE_STARTED; sendState(); }
    void onStop() { state &= ~STATE_STARTED; sendState(); }
    void onResume() { if (!bound) connect(); else if (overlay != null && !attached) attach(); state |= STATE_RESUMED; call(ON_RESUME); sendState(); }
    void onPause() { state &= ~STATE_RESUMED; call(ON_PAUSE); sendState(); }

    void destroy(boolean changingConfig) {
        if (overlay != null && attached) {
            Parcel d = Parcel.obtain();
            try {
                d.writeInterfaceToken(OVERLAY);
                d.writeInt(changingConfig ? 1 : 0);
                overlay.transact(Binder.FIRST_CALL_TRANSACTION + WINDOW_DETACHED - 1, d, null, IBinder.FLAG_ONEWAY);
            } catch (Exception ignored) { } finally { d.recycle(); }
        }
        attached = false;
        if (bound) { try { activity.unbindService(conn); } catch (Exception ignored) { } bound = false; }
        overlay = null;
    }

    // ---------- the swipe from Home ----------

    void startScroll() { scrolling = true; call(START_SCROLL); }

    void scroll(float progress) {
        if (!scrolling || overlay == null) return;
        Parcel d = Parcel.obtain();
        try {
            d.writeInterfaceToken(OVERLAY);
            d.writeFloat(U.clamp(progress, 0f, 1f));
            overlay.transact(Binder.FIRST_CALL_TRANSACTION + ON_SCROLL - 1, d, null, IBinder.FLAG_ONEWAY);
        } catch (Exception ignored) { } finally { d.recycle(); }
    }

    void endScroll() { if (scrolling) { scrolling = false; call(END_SCROLL); } }

    void open() { callInt(OPEN_OVERLAY, 1); }
    void close() { callInt(CLOSE_OVERLAY, 1); }

    private void sendState() { if (attached) callInt(SET_ACTIVITY_STATE, state); }

    private void call(int code) {
        if (overlay == null) return;
        Parcel d = Parcel.obtain();
        try {
            d.writeInterfaceToken(OVERLAY);
            overlay.transact(Binder.FIRST_CALL_TRANSACTION + code - 1, d, null, IBinder.FLAG_ONEWAY);
        } catch (Exception ignored) { } finally { d.recycle(); }
    }

    private void callInt(int code, int v) {
        if (overlay == null) return;
        Parcel d = Parcel.obtain();
        try {
            d.writeInterfaceToken(OVERLAY);
            d.writeInt(v);
            overlay.transact(Binder.FIRST_CALL_TRANSACTION + code - 1, d, null, IBinder.FLAG_ONEWAY);
        } catch (Exception ignored) { } finally { d.recycle(); }
    }
}
