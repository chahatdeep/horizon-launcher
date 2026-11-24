package com.praiselab.horizon;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * The Now page left of Home: weather, agenda, Microsoft To Do, focus timer and stories.
 * It is the bundled web page in feed mode; this class supplies calendar, location,
 * network and Microsoft sign-in to it.
 */
final class FeedView extends FrameLayout {
    private static final String HOST = "horizon.app";
    private final Launcher act;
    private final WebView web;
    private final Cloud cloud;
    private int insetTop, insetBottom;
    private String look;

    FeedView(Launcher a) {
        super(a);
        act = a;
        cloud = new Cloud(a);
        web = new WebView(a);
        web.setBackgroundColor(Color.TRANSPARENT);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setTextZoom(100);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.addJavascriptInterface(new Bridge(), "HorizonNative");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String path = u.getPath() == null ? "/" : u.getPath();
                if (path.equals("/") || path.equals("/index.html")) {
                    try { return new WebResourceResponse("text/html", "utf-8", act.getAssets().open("index.html")); }
                    catch (IOException e) { return null; }
                }
                return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, new ByteArrayInputStream(new byte[0]));
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                if (HOST.equals(req.getUrl().getHost())) return false;
                act.startSafely(new Intent(Intent.ACTION_VIEW, req.getUrl()));
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pushInsets();
                if (look != null) js("window.hz&&hz.setLook&&hz.setLook(" + look + ")");
            }
        });
        addView(web, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        web.loadUrl("https://" + HOST + "/index.html?mode=feed");
    }

    void setInsets(int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
        pushInsets();
    }

    void js(String code) { act.runOnUiThread(() -> web.evaluateJavascript(code, null)); }

    private void pushInsets() {
        float d = U.density;
        js("if(document.documentElement){document.documentElement.style.setProperty('--sat','" + (insetTop / d) + "px');"
                + "document.documentElement.style.setProperty('--sab','" + (insetBottom / d) + "px')}");
    }

    void refresh() { js("window.hz&&hz.refresh()"); }

    /** Hands the launcher's active look (palette, corner radius, mode) to the page. */
    void setLook(U.Pal p) {
        try {
            look = new JSONObject().put("dark", p.dark).put("black", p.black).put("style", p.style)
                    .put("bg", css(p.solid)).put("surface", css(p.surface)).put("solid", css(p.solid)).put("sunk", css(p.sunk))
                    .put("chip", css(p.chip)).put("fg", css(p.fg)).put("fg2", css(p.fg2)).put("fg3", css(p.fg3))
                    .put("accent", css(p.accent)).put("accentSoft", css(U.alpha(p.accent, 0.16f))).put("onAccent", css(p.onAccent))
                    .put("line", css(p.line)).put("danger", css(p.danger)).put("rim", p.rim == 0 ? "transparent" : css(p.rim))
                    .put("radius", p.radius).toString();
            js("window.hz&&hz.setLook&&hz.setLook(" + look + ")");
        } catch (Exception ignored) { }
    }

    private static String css(int c) {
        return "rgba(" + Color.red(c) + "," + Color.green(c) + "," + Color.blue(c) + "," + (Color.alpha(c) / 255f) + ")";
    }

    void back(ValueCallback<String> cb) { web.evaluateJavascript("window.hz?hz.back():0", cb); }

    void onPermission(int code, boolean ok) {
        if (code == Launcher.REQ_CAL) js("window.hz&&hz.onCalendarPermission(" + ok + ")");
        if (code == Launcher.REQ_LOC) js("window.hz&&hz.onLocationPermission(" + ok + ")");
    }

    /** Microsoft sends the browser back to horizon://auth?code=... after sign-in. */
    void handleAuthRedirect(Uri u) {
        if (u == null || !Cloud.REDIRECT.equals(u.getScheme() + "://" + u.getHost())) return;
        cloud.finishSignIn(u, (ok, msg) -> js("window.hz&&hz.onMsAuth(" + ok + "," + JSONObject.quote(msg) + ")"));
    }

    private Cloud.Reply replyTo(String id) {
        return (ok, body) -> js("window.hz&&hz.onFetch(" + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(body) + ")");
    }

    /** Exposed to the page as window.HorizonNative. */
    private final class Bridge {
        @JavascriptInterface
        public String apps() {
            JSONArray a = new JSONArray();
            for (Apps.App x : act.apps.all) {
                try {
                    a.put(new JSONObject().put("label", x.label).put("pkg", x.pkg).put("cmp", x.cn.flattenToString()).put("cat", x.category));
                } catch (Exception ignored) { }
            }
            return a.toString();
        }

        @JavascriptInterface
        public void launch(String cmp) {
            act.runOnUiThread(() -> {
                ComponentName cn = ComponentName.unflattenFromString(cmp);
                if (cn == null) return;
                Apps.App a = act.apps.first(cn.getPackageName());
                if (a != null) act.launchApp(a, web);
            });
        }

        @JavascriptInterface
        public void appInfo(String pkg) {
            act.startSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
        }

        @JavascriptInterface
        public void uninstall(String pkg) {
            act.startSafely(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
        }

        @JavascriptInterface
        public boolean calendarAllowed() {
            return act.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface
        public void requestCalendar() {
            act.runOnUiThread(() -> act.requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, Launcher.REQ_CAL));
        }

        @JavascriptInterface
        public String events(int days) {
            JSONArray a = new JSONArray();
            if (!calendarAllowed()) return a.toString();
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            long start = cal.getTimeInMillis() - 24L * 3600 * 1000;
            long end = start + (days + 2L) * 24 * 3600 * 1000;
            Uri.Builder ub = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(ub, start);
            ContentUris.appendId(ub, end);
            String[] proj = {
                    CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION,
                    CalendarContract.Instances.DISPLAY_COLOR, CalendarContract.Instances.SELF_ATTENDEE_STATUS
            };
            try (Cursor cur = act.getContentResolver().query(ub.build(), proj,
                    CalendarContract.Instances.VISIBLE + "=1", null, CalendarContract.Instances.BEGIN + " ASC")) {
                while (cur != null && cur.moveToNext()) {
                    if (cur.getInt(7) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue;
                    a.put(new JSONObject().put("id", cur.getLong(0)).put("title", cur.isNull(1) ? "" : cur.getString(1))
                            .put("begin", cur.getLong(2)).put("end", cur.getLong(3)).put("allDay", cur.getInt(4) == 1)
                            .put("loc", cur.isNull(5) ? "" : cur.getString(5)).put("color", cur.getInt(6)));
                }
            } catch (Exception ignored) { }
            return a.toString();
        }

        @JavascriptInterface
        public void openEvent(String id) {
            act.startSafely(new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, Long.parseLong(id))));
        }

        @JavascriptInterface
        public void newEvent() { act.startSafely(new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)); }

        @JavascriptInterface
        public void webSearch(String q) { act.runOnUiThread(() -> act.webSearch(q)); }

        @JavascriptInterface
        public void vibrate() {
            Vibrator v = act.getSystemService(Vibrator.class);
            if (v != null) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 160, 120, 160, 120, 260}, -1));
        }

        @JavascriptInterface
        public void homeSettings() { act.startSafely(new Intent(Settings.ACTION_HOME_SETTINGS)); }

        @JavascriptInterface
        public boolean isHome() { return act.isDefaultHome(); }

        @JavascriptInterface
        public void openUrl(String url) {
            Uri u = Uri.parse(url);
            if ("https".equals(u.getScheme()) || "http".equals(u.getScheme())) act.startSafely(new Intent(Intent.ACTION_VIEW, u));
        }

        @JavascriptInterface
        public void openGoogleFeed() { act.runOnUiThread(act::openGoogleApp); }

        @JavascriptInterface
        public void fetchText(String id, String url) { cloud.fetchText(url, replyTo(id)); }

        @JavascriptInterface
        public boolean locationAllowed() {
            return act.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface
        public void requestLocation() {
            act.runOnUiThread(() -> act.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, Launcher.REQ_LOC));
        }

        @JavascriptInterface
        public void requestLocationFix() {
            act.runOnUiThread(() -> {
                final double[][] fix = new double[1][];
                cloud.locate(
                        f -> { fix[0] = f; if (f == null) js("window.hz&&hz.onLocation(null)"); },
                        place -> js("window.hz&&hz.onLocation(" + fix[0][0] + "," + fix[0][1] + "," + JSONObject.quote(place) + ")"));
            });
        }

        @JavascriptInterface
        public String msStatus() {
            try { return new JSONObject().put("signedIn", cloud.signedIn()).put("name", cloud.accountName()).toString(); }
            catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface public String msGetClientId() { return cloud.clientId(); }
        @JavascriptInterface public void msSetClientId(String id) { cloud.setClientId(id); }

        @JavascriptInterface
        public void msSignIn() {
            if (!cloud.clientId().isEmpty()) act.startSafely(new Intent(Intent.ACTION_VIEW, cloud.authorizeUri()));
        }

        @JavascriptInterface public void msSignOut() { cloud.signOut(); }

        @JavascriptInterface
        public void msGraph(String id, String method, String path, String body) { cloud.graph(method, path, body, replyTo(id)); }
    }
}
