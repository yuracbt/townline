package com.chobotok.townline;

import java.util.ArrayList;
import java.util.List;

/**
 * Export/import of the user's configuration (manually added RSS/Atom feeds
 * and event pages, plus town and scan interval) as a human-readable CSV file.
 *
 * Format:
 *   # TownLine configuration — your feeds and event pages
 *   # town: Airdrie, AB
 *   # interval_minutes: 240
 *   type,name,url
 *   feed,"DiscoverAirdrie — Local News",https://www.discoverairdrie.com/rss/local-news
 *   event,"Airdrie Chamber — Business Events",https://www.airdriechamber.ab.ca/events/
 */
public class ConfigBackup {

    public static class Result {
        public int feedsAdded, eventsAdded, feedsSkipped, eventsSkipped;
        public boolean settingsApplied;
    }

    // ---------- export ----------

    public static String exportCsv(Prefs prefs, NewsDbHelper db) {
        StringBuilder sb = new StringBuilder();
        sb.append("# TownLine configuration — your feeds and event pages\n");
        sb.append("# town: ").append(prefs.getTown()).append('\n');
        sb.append("# interval_minutes: ").append(prefs.getIntervalMinutes()).append('\n');
        sb.append("type,name,url\n");
        for (Source s : db.getSources()) {
            sb.append("feed,").append(esc(s.name)).append(',').append(esc(s.url)).append('\n');
        }
        for (Link l : db.getLinks("events")) {
            sb.append("event,").append(esc(l.name)).append(',').append(esc(l.url)).append('\n');
        }
        return sb.toString();
    }

    private static String esc(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    // ---------- import ----------

    public static Result importCsv(String csv, Prefs prefs, NewsDbHelper db) {
        Result r = new Result();

        List<String> haveSources = new ArrayList<>();
        for (Source s : db.getSources()) haveSources.add(norm(s.url));
        List<String> haveEvents = new ArrayList<>();
        for (Link l : db.getLinks("events")) haveEvents.add(norm(l.url));

        String town = null;
        int intervalMinutes = -1;

        for (String rawLine : csv.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                if (line.startsWith("# town:")) {
                    town = line.substring("# town:".length()).trim();
                } else if (line.startsWith("# interval_minutes:")) {
                    try {
                        intervalMinutes =
                                Integer.parseInt(line.substring("# interval_minutes:".length()).trim());
                    } catch (NumberFormatException ignored) { }
                }
                continue;
            }
            List<String> cols = parseRow(line);
            if (cols.size() != 3) continue;
            if (cols.get(0).equalsIgnoreCase("type")) continue; // header row
            String type = cols.get(0).trim().toLowerCase();
            String name = cols.get(1).trim();
            String url = cols.get(2).trim();
            if (name.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) continue;

            if ("feed".equals(type)) {
                if (haveSources.contains(norm(url))) { r.feedsSkipped++; continue; }
                db.addSource(name, url, true, false);
                haveSources.add(norm(url));
                r.feedsAdded++;
            } else if ("event".equals(type)) {
                if (haveEvents.contains(norm(url))) { r.eventsSkipped++; continue; }
                db.addLink("events", name, url);
                haveEvents.add(norm(url));
                r.eventsAdded++;
            }
        }

        if (town != null && !town.isEmpty()) {
            prefs.setTown(town);
            r.settingsApplied = true;
        }
        if (intervalMinutes > 0) {
            prefs.setIntervalMinutes(intervalMinutes);
            r.settingsApplied = true;
        }
        return r;
    }

    /** Minimal CSV row parser that respects double-quoted fields. */
    private static List<String> parseRow(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                cols.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cols.add(cur.toString());
        return cols;
    }

    private static String norm(String url) {
        if (url == null) return "";
        String u = url.trim().toLowerCase();
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }
}
