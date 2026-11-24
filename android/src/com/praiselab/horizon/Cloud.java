package com.praiselab.horizon;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Network, location and Microsoft account plumbing for the page.
 * Microsoft sign-in is OAuth 2.0 authorization code + PKCE in the system browser; the refresh
 * token is encrypted with an Android Keystore key and never handed to the page.
 */
final class Cloud {
    static final String REDIRECT = "horizon://auth";
    private static final String AUTHORITY = "https://login.microsoftonline.com/common/oauth2/v2.0/";
    private static final String SCOPES = "offline_access User.Read Tasks.ReadWrite Calendars.Read";
    private static final String GRAPH = "https://graph.microsoft.com/v1.0";
    private static final String[] FETCH_ALLOW = {"https://news.google.com/", "https://api.open-meteo.com/"};
    private static final String KEY_ALIAS = "horizon-ms";

    private final Context ctx;
    private final SharedPreferences prefs;
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final SecureRandom rnd = new SecureRandom();
    private String accessToken;
    private long accessExpiry;

    Cloud(Context ctx) {
        this.ctx = ctx;
        this.prefs = ctx.getSharedPreferences("ms", Context.MODE_PRIVATE);
    }

    // ---------- plain fetch (allowlisted hosts only) ----------

    interface Reply { void send(boolean ok, String body); }

    void fetchText(String url, Reply reply) {
        boolean allowed = false;
        for (String p : FETCH_ALLOW) if (url.startsWith(p)) allowed = true;
        if (!allowed) { reply.send(false, "blocked host"); return; }
        io.execute(() -> {
            try {
                HttpURLConnection c = open(url, "GET");
                int code = c.getResponseCode();
                String body = read(code < 400 ? c.getInputStream() : c.getErrorStream());
                reply.send(code < 400, code < 400 ? body : "HTTP " + code);
            } catch (Exception e) {
                reply.send(false, String.valueOf(e.getMessage()));
            }
        });
    }

    // ---------- location ----------

    /** Delivers (lat, lon, place) or null when no fix is available. */
    void locate(Consumer<double[]> onFix, Consumer<String> onPlace) {
        LocationManager lm = ctx.getSystemService(LocationManager.class);
        Location best = null;
        try {
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException e) {
            onFix.accept(null);
            return;
        }
        final Location fallback = best;
        Consumer<Location> deliver = l -> {
            if (l == null) { onFix.accept(null); return; }
            onFix.accept(new double[]{l.getLatitude(), l.getLongitude()});
            io.execute(() -> {
                String place = "";
                try {
                    List<Address> a = new Geocoder(ctx).getFromLocation(l.getLatitude(), l.getLongitude(), 1);
                    if (a != null && !a.isEmpty()) {
                        Address ad = a.get(0);
                        place = ad.getLocality() != null ? ad.getLocality()
                                : ad.getSubAdminArea() != null ? ad.getSubAdminArea() : "";
                    }
                } catch (Exception ignored) { }
                onPlace.accept(place);
            });
        };
        if (best != null && System.currentTimeMillis() - best.getTime() < 60L * 60 * 1000) { deliver.accept(best); return; }
        String provider = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER
                : lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) ? LocationManager.FUSED_PROVIDER
                : LocationManager.GPS_PROVIDER;
        try {
            lm.getCurrentLocation(provider, null, ctx.getMainExecutor(), l -> deliver.accept(l != null ? l : fallback));
        } catch (Exception e) {
            deliver.accept(fallback);
        }
    }

    // ---------- Microsoft account ----------

    String clientId() { return prefs.getString("client_id", ""); }
    void setClientId(String id) { prefs.edit().putString("client_id", id.trim()).apply(); }
    boolean signedIn() { return prefs.contains("rt"); }
    String accountName() { return prefs.getString("name", ""); }

    /** Builds the authorize URL and remembers the PKCE verifier for the redirect. */
    Uri authorizeUri() {
        String verifier = b64url(randomBytes(32));
        String state = b64url(randomBytes(16));
        prefs.edit().putString("pkce", verifier).putString("state", state).apply();
        String challenge;
        try {
            challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) { throw new IllegalStateException(e); }
        return Uri.parse(AUTHORITY + "authorize").buildUpon()
                .appendQueryParameter("client_id", clientId())
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", REDIRECT)
                .appendQueryParameter("response_mode", "query")
                .appendQueryParameter("scope", SCOPES)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("state", state)
                .appendQueryParameter("prompt", "select_account")
                .build();
    }

    /** Handles horizon://auth?code=...; reply gets (ok, account name or error message). */
    void finishSignIn(Uri redirect, Reply reply) {
        String err = redirect.getQueryParameter("error_description");
        String code = redirect.getQueryParameter("code");
        String state = redirect.getQueryParameter("state");
        if (code == null) { reply.send(false, err != null ? err : "Sign-in was cancelled"); return; }
        if (state == null || !state.equals(prefs.getString("state", null))) { reply.send(false, "Sign-in expired. Try again."); return; }
        String verifier = prefs.getString("pkce", "");
        io.execute(() -> {
            try {
                JSONObject t = tokenRequest("grant_type=authorization_code"
                        + "&code=" + Uri.encode(code)
                        + "&redirect_uri=" + Uri.encode(REDIRECT)
                        + "&code_verifier=" + Uri.encode(verifier));
                JSONObject me = new JSONObject(graphRaw("GET", "/me?$select=displayName,mail,userPrincipalName", null));
                String name = me.optString("mail", "");
                if (name.isEmpty() || "null".equals(name)) name = me.optString("userPrincipalName", me.optString("displayName", "Microsoft account"));
                prefs.edit().putString("name", name).remove("pkce").remove("state").apply();
                reply.send(true, name);
            } catch (Exception e) {
                signOut();
                reply.send(false, "Microsoft sign-in failed: " + e.getMessage());
            }
        });
    }

    void signOut() {
        accessToken = null;
        accessExpiry = 0;
        prefs.edit().remove("rt").remove("name").remove("pkce").remove("state").apply();
    }

    /** Graph call for the page; only /me/... paths are allowed. */
    void graph(String method, String path, String body, Reply reply) {
        if (!path.startsWith("/me/") && !path.startsWith("/me?")) { reply.send(false, "blocked path"); return; }
        if (!method.matches("GET|POST|PATCH")) { reply.send(false, "blocked method"); return; }
        io.execute(() -> {
            try {
                reply.send(true, graphRaw(method, path, body == null || body.isEmpty() ? null : body));
            } catch (Exception e) {
                reply.send(false, String.valueOf(e.getMessage()));
            }
        });
    }

    private String graphRaw(String method, String path, String body) throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection c = open(GRAPH + path, method);
            c.setRequestProperty("Authorization", "Bearer " + token(attempt > 0));
            c.setRequestProperty("Prefer", "outlook.timezone=\"UTC\"");
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
            }
            int code = c.getResponseCode();
            if (code == 401 && attempt == 0) continue;
            String text = read(code < 400 ? c.getInputStream() : c.getErrorStream());
            if (code >= 400) throw new Exception("HTTP " + code + " " + text);
            return text;
        }
        throw new Exception("HTTP 401");
    }

    private synchronized String token(boolean forceRefresh) throws Exception {
        if (!forceRefresh && accessToken != null && System.currentTimeMillis() < accessExpiry - 60_000) return accessToken;
        String rt = prefs.getString("rt", null);
        if (rt == null) throw new Exception("not signed in");
        tokenRequest("grant_type=refresh_token&refresh_token=" + Uri.encode(decrypt(rt)));
        return accessToken;
    }

    private synchronized JSONObject tokenRequest(String grant) throws Exception {
        HttpURLConnection c = open(AUTHORITY + "token", "POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        String form = "client_id=" + Uri.encode(clientId()) + "&scope=" + Uri.encode(SCOPES) + "&" + grant;
        try (OutputStream o = c.getOutputStream()) { o.write(form.getBytes(StandardCharsets.UTF_8)); }
        int code = c.getResponseCode();
        JSONObject j = new JSONObject(read(code < 400 ? c.getInputStream() : c.getErrorStream()));
        if (code >= 400) {
            if ("invalid_grant".equals(j.optString("error"))) signOut();
            throw new Exception(j.optString("error_description", "HTTP " + code).split("\r?\n")[0]);
        }
        accessToken = j.getString("access_token");
        accessExpiry = System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000;
        String newRt = j.optString("refresh_token", "");
        if (!newRt.isEmpty()) prefs.edit().putString("rt", encrypt(newRt)).apply();
        return j;
    }

    // ---------- helpers ----------

    private static HttpURLConnection open(String url, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(10_000);
        c.setReadTimeout(15_000);
        c.setRequestProperty("User-Agent", "Horizon/1.0 (Android)");
        c.setRequestProperty("Accept", "*/*");
        return c;
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = s.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    private byte[] randomBytes(int n) { byte[] b = new byte[n]; rnd.nextBytes(b); return b; }
    private static String b64url(byte[] b) { return Base64.encodeToString(b, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP); }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            kg.generateKey();
        }
        return (SecretKey) ks.getKey(KEY_ALIAS, null);
    }

    private String encrypt(String s) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] ct = c.doFinal(s.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP);
    }

    private String decrypt(String s) throws Exception {
        String[] p = s.split(":");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(p[0], Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(p[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
}
