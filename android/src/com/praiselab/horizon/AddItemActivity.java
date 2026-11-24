package com.praiselab.horizon;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.Bundle;

/** Handles "Add to Home screen" requests from other apps (pinned shortcuts and widgets). */
public class AddItemActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(Prefs.scaled(base)); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(this);
        LauncherApps la = getSystemService(LauncherApps.class);
        LauncherApps.PinItemRequest req = la.getPinItemRequest(getIntent());
        if (req == null || !req.isValid()) { finish(); return; }
        boolean shortcut = req.getRequestType() == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT;
        String label;
        if (shortcut) {
            ShortcutInfo si = req.getShortcutInfo();
            label = si == null ? "this shortcut" : String.valueOf(si.getShortLabel() != null ? si.getShortLabel() : si.getLongLabel());
        } else {
            AppWidgetProviderInfo w = req.getAppWidgetProviderInfo(this);
            label = w == null ? "this widget" : w.loadLabel(getPackageManager());
        }
        U.Pal pal = U.pal(this);
        new AlertDialog.Builder(this, pal.dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Add to Home?")
                .setMessage("Add “" + label + "” to your Horizon Home screen.")
                .setPositiveButton("Add", (d, w) -> accept(req, shortcut))
                .setNegativeButton("Cancel", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void accept(LauncherApps.PinItemRequest req, boolean shortcut) {
        Item it = new Item();
        Apps apps = new Apps(this);
        if (shortcut) {
            ShortcutInfo si = req.getShortcutInfo();
            if (si == null || !req.accept()) { finish(); return; }
            it.type = Item.SHORTCUT;
            it.pkg = si.getPackage();
            it.shortcutId = si.getId();
            it.user = apps.serial(si.getUserHandle());
            it.title = String.valueOf(si.getShortLabel() != null ? si.getShortLabel() : si.getLongLabel());
        } else {
            AppWidgetProviderInfo info = req.getAppWidgetProviderInfo(this);
            if (info == null) { finish(); return; }
            AppWidgetHost host = new AppWidgetHost(this, Launcher.HOST_ID);
            int id = host.allocateAppWidgetId();
            AppWidgetManager awm = AppWidgetManager.getInstance(this);
            boolean bound = false;
            try { bound = awm.bindAppWidgetIdIfAllowed(id, info.getProfile(), info.provider, null); } catch (Exception ignored) { }
            Bundle extras = new Bundle();
            extras.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            if (!bound || !req.accept(extras)) {
                host.deleteAppWidgetId(id);
                U.toast(this, "Couldn't add that widget");
                finish();
                return;
            }
            it.type = Item.WIDGET;
            it.widgetId = id;
            it.provider = info.provider.flattenToString();
            it.pkg = info.provider.getPackageName();
            it.user = apps.serial(info.getProfile());
            it.spanX = 4;
            it.spanY = 2;
        }
        if (Launcher.current != null) {
            Launcher.current.addPinnedItem(it);
        } else {
            Store s = new Store(this);
            int[] at = s.findFree(Item.DESKTOP, 0, new Prefs(this).cols(), 200, it.spanX, it.spanY, null);
            it.x = at != null ? at[0] : 0;
            it.y = at != null ? at[1] : 0;
            s.add(it);
            s.save();
        }
        U.toast(this, "Added to Home");
        finish();
    }
}
