package com.praiselab.horizon;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/** Only used to lock the screen on double-tap. It reads no screen content. */
public class LockService extends AccessibilityService {
    private static LockService instance;

    @Override protected void onServiceConnected() { instance = this; }
    @Override public boolean onUnbind(Intent intent) { instance = null; return super.onUnbind(intent); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    static boolean lock() {
        return instance != null && instance.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }
}
