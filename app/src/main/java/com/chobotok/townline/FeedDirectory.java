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
    }

    /** Feeds whose match keys fit the town. Keys: town substring, "*alberta", "*canada". */
    public static List<Entry> suggestionsFor(Context ctx, String town) {
        List<Entry> out = new ArrayList<>();
        String t = town == null ? "" : town.toLowerCase();
        boolean alberta = t.contains("alberta") || t.contains(", ab") || t.endsWith(" ab");
        try {
            InputStream in = ctx.getAssets().open("feeds_directory.json");
            byte[] buf = new byte[in.available()];
            int n = 0, r;
            while ((r = in.read(buf, n, buf.length - n)) > 0) n += r;
            in.close();
            JSONArray feeds = new JSONObject(new String(buf, 0, n, "UTF-8")).getJSONArray("feeds");
            for (int i = 0; i < feeds.length(); i++) {
                JSONObject f = feeds.getJSONObject(i);
                JSONArray match = f.getJSONArray("match");
                for (int j = 0; j < match.length(); j++) {
                    String key = match.getString(j);
                    boolean hit = key.equals("*canada")
                            || (key.equals("*alberta") && alberta)
                            || (!key.startsWith("*") && t.contains(key));
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
