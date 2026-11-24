package com.praiselab.horizon;

import android.content.pm.ApplicationInfo;

import java.util.Locale;

/** Sorts apps into drawer categories: Android's own app category first, then well-known names. */
final class AppCategories {
    static final String[] ORDER = {"Social", "Work", "Money", "Shopping", "Travel", "Media", "Games", "Photos", "News", "Health", "Home", "Tools", "Other"};

    private static final String[][] WORDS = {
            {"Social", "whatsapp", "messag", "instagram", "facebook", "discord", "linkedin", "slack", "telegram", "snapchat", "reddit", "twitter", "com.twitter", "threads", "signal", "meetup", "pinterest", "piazza", "wechat", "tiktok", "messenger", "steam chat", "bluesky", "mastodon"},
            {"Work", "outlook", "gmail", "teams", "office", "word", "excel", "powerpoint", "onedrive", "drive", "docs", "sheets", "slides", "notion", "todo", "to do", "tasks", "keep", "calendar", "zoom", "webex", "acrobat", "adobe scan", "fill & sign", "anyconnect", "anydesk", "duo", "authenticator", "chatgpt", "claude", "gemini", "copilot", "overleaf", "dropbox", "evernote", "trello", "jira", "amazon business", "canvas", "atrium", "cuboulder", "scholar"},
            {"Money", "bank", "chase", "amex", "american express", "discover", "capital one", "tiaa", "paypal", "venmo", "cash app", "wallet", "coinbase", "binance", "crypto", "metamask", "solflare", "coin", "robinhood", "fidelity", "schwab", "vanguard", "zelle", "wells", "citi", "credit karma", "mint", "slickdeals", "freewallet", "pay"},
            {"Shopping", "amazon shopping", "shopping", "ebay", "walmart", "target", "costco", "blinkit", "instacart", "doordash", "ubereats", "uber eats", "grubhub", "etsy", "temu", "shein", "best buy", "flipkart", "swiggy", "zomato", "ikea", "parcel"},
            {"Travel", "maps", "uber", "lyft", "airbnb", "booking", "expedia", "airline", "air france", "delta", "united", "southwest", "avis", "hertz", "recreation.gov", "nps", "transit", "waze", "parkmobile", "mazda", "tesla", "trip", "hotel", "marriott", "hilton"},
            {"Media", "spotify", "youtube", "netflix", "prime video", "disney", "hulu", "max", "twitch", "music", "podcast", "audible", "plex", "iptv", "amc", "bbc", "pandora", "soundcloud", "player", "tv", "stream", "kindle"},
            {"Games", "game", "8 ball", "pool", "chess", "puzzle", "steam", "xbox", "playstation", "clash", "candy"},
            {"Photos", "camera", "photo", "gallery", "lightroom", "snapseed", "picsart", "photopills", "expert raw", "lens", "capture", "vsco", "stellarium"},
            {"News", "news", "bbc news", "cnn", "times", "reuters", "flipboard", "medium", "weather"},
            {"Health", "health", "fit", "sleep", "workout", "strava", "fitbit", "myfitnesspal", "calm", "headspace", "skin", "doctor", "pharmacy", "medical", "sydney health"},
            {"Home", "home", "smartthings", "tuya", "alexa", "google home", "nest", "ring", "hue", "ambient", "creality", "bambu", "bose", "bluetooth", "printer", "apartment", "resident"},
            {"Tools", "calculator", "clock", "files", "my files", "settings", "browser", "chrome", "brave", "firefox", "edge", "samsung internet", "contacts", "phone", "dialer", "recorder", "notes", "level", "meter", "scanner", "translate", "apk", "assistant", "bixby", "analytics", "vpn", "airtel", "t-life", "carrier", "store", "galaxy", "play store", "tool"},
    };

    static String of(Apps.App a) {
        String hay = (a.label + " " + a.pkg).toLowerCase(Locale.ROOT);
        for (String[] group : WORDS)
            for (int k = 1; k < group.length; k++)
                if (hay.contains(group[k])) return group[0];
        switch (a.category) {
            case ApplicationInfo.CATEGORY_GAME: return "Games";
            case ApplicationInfo.CATEGORY_AUDIO:
            case ApplicationInfo.CATEGORY_VIDEO: return "Media";
            case ApplicationInfo.CATEGORY_IMAGE: return "Photos";
            case ApplicationInfo.CATEGORY_SOCIAL: return "Social";
            case ApplicationInfo.CATEGORY_NEWS: return "News";
            case ApplicationInfo.CATEGORY_MAPS: return "Travel";
            case ApplicationInfo.CATEGORY_PRODUCTIVITY: return "Work";
            case ApplicationInfo.CATEGORY_ACCESSIBILITY: return "Tools";
        }
        return a.system ? "Tools" : "Other";
    }
}
