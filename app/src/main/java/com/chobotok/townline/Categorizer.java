package com.chobotok.townline;

/** Assigns a category to each story so the line can be filtered. */
public class Categorizer {

    private static final String[] EVENT_WORDS = {
            "festival", "concert", "market", "workshop", "fair", "parade",
            "fundraiser", "tournament", "exhibition", "celebration", "ceremony",
            "live music", "trivia", "bingo", "open house", "rodeo", "carnival",
            "powwow", "car show", "art walk"
    };

    private static final String[] BUSINESS_WORDS = {
            "chamber of commerce", "grand opening", "now open", "now hiring",
            "job fair", "business licence", "business license"
    };

    public static String categorize(String sourceName, String title, String description) {
        if ("Saved".equals(sourceName)) return "Saved";
        String t = ((title == null ? "" : title) + " " + (description == null ? "" : description))
                .toLowerCase();
        if (containsAny(t, EVENT_WORDS)) return "Events";
        if (containsAny(t, BUSINESS_WORDS)) return "Business";
        String s = sourceName == null ? "" : sourceName.toLowerCase();
        if (s.contains("community")) return "Community";
        if (s.contains("calgary")) return "Calgary";
        return "News";
    }

    private static boolean containsAny(String text, String[] words) {
        for (String w : words) {
            if (text.contains(w)) return true;
        }
        return false;
    }
}
