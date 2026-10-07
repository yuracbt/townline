package com.chobotok.townline;

import android.content.Context;
import java.util.List;

/**
 * Assigns a category to each story so the line can be filtered.
 *
 * Smart rules: the feed's own name is the strongest signal
 * ("Ukrainska Pravda" → Ukraine, "CBC Calgary" → Calgary), then the story
 * content. City categories are specific (Calgary, Edmonton…); anything else
 * Albertan lands in the "Alberta" bucket. Call {@link #rethinkAll} to
 * re-apply the rules to every stored story on demand.
 */
public class Categorizer {

    // ----- source-name signals -----

    private static final String[] UKRAINE_SRC = {"ukrain", "pravda", "kyiv"};

    /** Ukrainian outlets recognizable by domain alone, whatever the feed is named. */
    private static final String[] UKRAINE_URL =
            {"pravda", "ukrinform", "kyivindependent", "kyivpost", ".ua/"};

    private static final String[] BUSINESS_SRC =
            {"business", "chamber", "economic", "trade"};

    private static final String[] SPORT_SRC = {"sport"};

    private static final String[] COMMUNITY_SRC = {"community"};

    /** Alberta cities/towns that earn their own category when named in a feed. */
    private static final String[][] AB_CITIES = {
            {"calgary", "Calgary"},
            {"edmonton", "Edmonton"},
            {"red deer", "Red Deer"},
            {"lethbridge", "Lethbridge"},
            {"airdrie", "Airdrie"},
            {"grande prairie", "Grande Prairie"},
            {"medicine hat", "Medicine Hat"},
            {"fort mcmurray", "Fort McMurray"},
            {"cochrane", "Cochrane"},
            {"okotoks", "Okotoks"},
            {"spruce grove", "Spruce Grove"},
            {"st. albert", "St. Albert"},
            {"banff", "Banff"},
            {"canmore", "Canmore"},
            {"jasper", "Jasper"},
    };

    // ----- content signals -----

    private static final String[] UKRAINE_WORDS = {
            "ukraine", "ukrainian", "kyiv", "kiev", "zelensky", "zelenskiy"
    };

    private static final String[] EVENT_WORDS = {
            "festival", "concert", "market", "workshop", "fair", "parade",
            "fundraiser", "tournament", "exhibition", "celebration", "ceremony",
            "live music", "trivia", "bingo", "open house", "rodeo", "carnival",
            "powwow", "car show", "art walk"
    };

    private static final String[] BUSINESS_WORDS = {
            "chamber of commerce", "grand opening", "now open", "now hiring",
            "job fair", "business licence", "business license",
            "business", "economy", "economic", "entrepreneur", "startup",
            "investment", "investor", "stock market", "housing market",
            "job market", "real estate", "inflation", "interest rate", "oil price"
    };

    private static final String[] SPORT_WORDS = {
            "hockey", "nhl", "flames", "oilers", "stampeders", "elks",
            "soccer", "fifa", "basketball", "raptors", "baseball",
            "blue jays", "tennis", "golf", "pga", "olympics",
            "stanley cup", "grey cup", "world cup", "ufc", "mma"
    };

    private static final String[] COMMUNITY_WORDS = {
            "community", "neighbourhood", "neighborhood", "volunteer",
            "school board", "city council", "neighbours", "neighbors"
    };

    /**
     * Category for one story. A feed's own beat (Ukraine, Business, Sport,
     * Community) wins first; the feed's URL gets a vote too (a pravda.com.ua
     * feed is Ukraine no matter what you named it); then what the story is
     * actually about — specific cities get their own shelf, the rest of
     * Alberta shares one; the feed's home town is the fallback.
     */
    public static String categorize(String sourceName, String sourceUrl,
                                    String title, String description) {
        if ("Saved".equals(sourceName)) return "Saved";
        String s = lower(sourceName);
        String u = lower(sourceUrl);
        String t = lower(title) + " " + lower(description);

        // 1. the feed's own beat
        if (containsAny(s, UKRAINE_SRC)) return "Ukraine";
        if (containsAny(s, BUSINESS_SRC)) return "Business";
        if (containsAny(s, SPORT_SRC)) return "Sport";
        if (containsAny(s, COMMUNITY_SRC)) return "Community";

        // 2. the feed's URL speaks when the name doesn't
        if (containsAny(u, UKRAINE_URL)) return "Ukraine";

        // 3. what the story is about: topics, then places
        if (containsAny(t, UKRAINE_WORDS)) return "Ukraine";
        if (t.contains("calgary")) return "Calgary";
        if (t.contains("edmonton")) return "Edmonton";
        if (t.contains("alberta") || matchCity(t) != null) return "Alberta";

        // 4. the feed's home town as fallback (URL first, then name)
        String city = matchCity(u);
        if (city == null) city = matchCity(s);
        if (city != null) return city;

        // 5. general story topics
        if (containsAny(t, EVENT_WORDS)) return "Events";
        if (containsAny(t, BUSINESS_WORDS)) return "Business";
        if (containsAny(t, SPORT_WORDS)) return "Sport";
        if (containsAny(t, COMMUNITY_WORDS)) return "Community";
        return "News";
    }

    /**
     * What the app thinks a source is about, from its name and URL alone —
     * shown next to each feed in Settings so the thinking is visible.
     */
    public static String thinkSource(String sourceName, String sourceUrl) {
        if ("Saved".equals(sourceName)) return "Saved";
        String s = lower(sourceName);
        String u = lower(sourceUrl);
        if (containsAny(s, UKRAINE_SRC) || containsAny(u, UKRAINE_URL)) return "Ukraine";
        if (containsAny(s, BUSINESS_SRC)) return "Business";
        if (containsAny(s, SPORT_SRC)) return "Sport";
        if (containsAny(s, COMMUNITY_SRC)) return "Community";
        String city = matchCity(u);
        if (city == null) city = matchCity(s);
        return city != null ? city : "General";
    }

    /**
     * Re-applies the current rules to every stored story. Run on demand —
     * e.g. after adding new feeds — so filters always reflect the latest
     * thinking. Returns the number of stories re-examined.
     */
    public static int rethinkAll(Context ctx) {
        NewsDbHelper db = new NewsDbHelper(ctx);
        List<NewsItem> all = db.getAllForRecategorize();
        for (NewsItem n : all) {
            if ("Saved".equals(n.category)) continue; // user-saved links keep their shelf
            db.setCategory(n.id,
                    categorize(n.sourceName, n.sourceUrl, n.title, n.description));
        }
        int count = all.size();
        db.close();
        return count;
    }

    private static String matchCity(String text) {
        for (String[] c : AB_CITIES) {
            if (text.contains(c[0])) return c[1];
        }
        return null;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    private static boolean containsAny(String text, String[] words) {
        for (String w : words) {
            if (text.contains(w)) return true;
        }
        return false;
    }
}
