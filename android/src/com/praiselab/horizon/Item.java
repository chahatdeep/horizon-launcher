package com.praiselab.horizon;

import org.json.JSONException;
import org.json.JSONObject;

/** One thing placed by the user: an app, folder, widget, pinned shortcut or the at-a-glance header. */
final class Item {
    static final int APP = 0, FOLDER = 1, WIDGET = 2, SHORTCUT = 3, GLANCE = 4, MEDIA = 5, STACK = 6;
    /** Containers: the home tiles, the dock, the app drawer (transient), the Widgets page, or a folder's id. */
    static final long DESKTOP = -100, HOTSEAT = -101, DRAWER = -102, PANEL = -103;

    long id;
    int type;
    long container = DESKTOP;
    int screen, x, y, spanX = 1, spanY = 1;
    String cmp = "", pkg = "", shortcutId = "", title = "", provider = "";
    long user;
    int widgetId = -1;

    String key() { return cmp + "#" + user; }

    boolean isIcon() { return type == APP || type == SHORTCUT || type == FOLDER; }

    static Item app(Apps.App a) {
        Item i = new Item();
        i.type = APP;
        i.cmp = a.cn.flattenToString();
        i.pkg = a.pkg;
        i.user = a.serial;
        i.title = a.label;
        return i;
    }

    Item copy() {
        Item i = new Item();
        i.id = id; i.type = type; i.container = container; i.screen = screen; i.x = x; i.y = y;
        i.spanX = spanX; i.spanY = spanY; i.cmp = cmp; i.pkg = pkg; i.shortcutId = shortcutId;
        i.title = title; i.provider = provider; i.user = user; i.widgetId = widgetId;
        return i;
    }

    JSONObject json() throws JSONException {
        return new JSONObject().put("id", id).put("type", type).put("container", container)
                .put("screen", screen).put("x", x).put("y", y).put("spanX", spanX).put("spanY", spanY)
                .put("cmp", cmp).put("pkg", pkg).put("shortcutId", shortcutId).put("title", title)
                .put("provider", provider).put("user", user).put("widgetId", widgetId);
    }

    static Item from(JSONObject o) {
        Item i = new Item();
        i.id = o.optLong("id");
        i.type = o.optInt("type");
        i.container = o.optLong("container", DESKTOP);
        i.screen = o.optInt("screen");
        i.x = o.optInt("x");
        i.y = o.optInt("y");
        i.spanX = Math.max(1, o.optInt("spanX", 1));
        i.spanY = Math.max(1, o.optInt("spanY", 1));
        i.cmp = o.optString("cmp");
        i.pkg = o.optString("pkg");
        i.shortcutId = o.optString("shortcutId");
        i.title = o.optString("title");
        i.provider = o.optString("provider");
        i.user = o.optLong("user");
        i.widgetId = o.optInt("widgetId", -1);
        return i;
    }
}
