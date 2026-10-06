package com.chobotok.townline;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Curated directory of local-news RSS feeds (assets/feeds_directory.json).
 * Matches feeds to the user's town and verifies they actually work
 * before suggesting them.
 */
public class FeedDirectory {

    public static class Entry {
        public String name;
        public String url;
        /** "feed" (RSS, verified before suggesting) or "place" (event page link). */
        public String kind;
    }

    /** Feeds whose match keys fit the town. Keys: town substring, "*alberta", "*canada". */
    public static List<Entry> suggestionsFor(Context ctx, String town) {
        List<Entry> out = loadMatches(ctx, "feeds_directory.json", "feeds", town);
        for (Entry e : out) e.kind = "feed";
        return out;
    }

    /** Event/business/library pages for the town. Curated, no runtime check needed. */
    public static List<Entry> placesFor(Context ctx, String town) {
        List<Entry> out = loadMatches(ctx, "places_directory.json", "places", town);
        for (Entry e : out) e.kind = "place";
        return out;
    }

    private static List<Entry> loadMatches(Context ctx, String file, String key, String town) {
        List<Entry> out = new ArrayList<>();
        String t = town == null ? "" : town.toLowerCase();
        boolean alberta = t.contains("alberta") || t.contains(", ab") || t.endsWith(" ab");
        try {
            String json = readAsset(ctx, file);
            if (json == null) return out;
            JSONArray arr = new JSONObject(json).getJSONArray(key);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject f = arr.getJSONObject(i);
                JSONArray match = f.getJSONArray("match");
                for (int j = 0; j < match.length(); j++) {
                    String mkey = match.getString(j);
                    boolean hit = mkey.equals("*canada")
                            || (mkey.equals("*alberta") && alberta)
                            || (!mkey.startsWith("*") && t.contains(mkey));
                    if (hit) {
                        Entry e = new Entry();
                        e.name = f.getString("name");
                        e.url = f.getString("url");
                        out.add(e);
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Total directory entries (feeds + places), unfiltered. 0 means the asset is missing. */
    public static int totalEntries(Context ctx) {
        return loadAll(ctx, "feeds_directory.json", "feeds").size()
                + loadAll(ctx, "places_directory.json", "places").size();
    }

    private static List<Entry> loadAll(Context ctx, String file, String key) {
        List<Entry> out = new ArrayList<>();
        try {
            String json = readAsset(ctx, file);
            if (json == null) return out;
            JSONArray arr = new JSONObject(json).getJSONArray(key);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject f = arr.getJSONObject(i);
                Entry e = new Entry();
                e.name = f.getString("name");
                e.url = f.getString("url");
                out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Reads a whole asset file. available() is unreliable for compressed APK assets. */
    private static String readAsset(Context ctx, String file) {
        InputStream in = null;
        try {
            in = ctx.getAssets().open(file);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int r;
            while ((r = in.read(tmp)) != -1) baos.write(tmp, 0, r);
            return baos.toString("UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
        }
    }

    /** True if the URL returns a parseable RSS/Atom feed. Call off the main thread. */
    public static boolean isWorkingFeed(String urlStr) {
        HttpURLConnection c = null;
        try {
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) TownLine/1.4");
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) return false;
            InputStream in = c.getInputStream();
            byte[] buf = new byte[65536];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            in.close();
            String head = new String(buf, 0, n, "UTF-8").toLowerCase();
            boolean feed = head.contains("<rss") || head.contains("<feed");
            boolean items = head.contains("<item") || head.contains("<entry");
            return feed && items;
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
