package com.chobotok.townline;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Parses RSS 2.0 and Atom feeds into plain items. No dependencies. */
public class RssParser {

    public static class Parsed {
        public String title = "";
        public String link = "";
        public String guid = "";
        public String description = "";
        public long pubDate = 0;
    }

    public static List<Parsed> parse(InputStream in) throws Exception {
        XmlPullParser x = Xml.newPullParser();
        x.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        x.setInput(in, null);

        // find root element to decide format
        int ev;
        String root = null;
        while ((ev = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                root = x.getName();
                break;
            }
        }
        if (root == null) return new ArrayList<>();
        if (root.equalsIgnoreCase("feed")) return parseAtom(x);
        return parseRss(x);
    }

    // x is positioned at <rss> or <channel> start tag
    private static List<Parsed> parseRss(XmlPullParser x) throws Exception {
        List<Parsed> out = new ArrayList<>();
        int depth = x.getDepth();
        int ev;
        while ((ev = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && x.getName().equalsIgnoreCase("item")
                    && x.getDepth() == depth + 2) {
                Parsed p = readRssItem(x);
                if (!p.title.isEmpty() && !p.link.isEmpty()) out.add(p);
            } else if (ev == XmlPullParser.END_TAG && x.getDepth() == depth
                    && x.getName().equalsIgnoreCase("rss")) {
                break;
            }
        }
        return out;
    }

    private static Parsed readRssItem(XmlPullParser x) throws Exception {
        Parsed p = new Parsed();
        String publisher = "";
        int depth = x.getDepth();
        int ev;
        while ((ev = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG && x.getDepth() == depth
                    && x.getName().equalsIgnoreCase("item")) break;
            if (ev != XmlPullParser.START_TAG) continue;
            String tag = x.getName().toLowerCase(Locale.US);
            switch (tag) {
                case "title": p.title = clean(readText(x)); break;
                case "link": p.link = readText(x).trim(); break;
                case "guid": p.guid = readText(x).trim(); break;
                case "description":
                case "content:encoded": {
                    String d = readText(x);
                    if (p.description.isEmpty()) p.description = snippet(d);
                    break;
                }
                case "pubdate": p.pubDate = parseDate(readText(x).trim()); break;
                case "source": publisher = clean(readText(x)); break;
                default: skip(x); break;
            }
        }
        if (p.guid.isEmpty()) p.guid = p.link;
        if (!publisher.isEmpty() && !p.description.isEmpty()) {
            p.description = "via " + publisher + " — " + p.description;
        } else if (!publisher.isEmpty()) {
            p.description = "via " + publisher;
        }
        if (p.pubDate == 0) p.pubDate = System.currentTimeMillis();
        return p;
    }

    // x is positioned at <feed> start tag
    private static List<Parsed> parseAtom(XmlPullParser x) throws Exception {
        List<Parsed> out = new ArrayList<>();
        int depth = x.getDepth();
        int ev;
        while ((ev = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && x.getName().equalsIgnoreCase("entry")
                    && x.getDepth() == depth + 1) {
                Parsed p = readAtomEntry(x);
                if (!p.title.isEmpty() && !p.link.isEmpty()) out.add(p);
            } else if (ev == XmlPullParser.END_TAG && x.getDepth() == depth
                    && x.getName().equalsIgnoreCase("feed")) {
                break;
            }
        }
        return out;
    }

    private static Parsed readAtomEntry(XmlPullParser x) throws Exception {
        Parsed p = new Parsed();
        int depth = x.getDepth();
        int ev;
        while ((ev = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG && x.getDepth() == depth
                    && x.getName().equalsIgnoreCase("entry")) break;
            if (ev != XmlPullParser.START_TAG) continue;
            String tag = x.getName().toLowerCase(Locale.US);
            switch (tag) {
                case "title": p.title = clean(readText(x)); break;
                case "id": p.guid = readText(x).trim(); break;
                case "link": {
                    String href = x.getAttributeValue(null, "href");
                    String rel = x.getAttributeValue(null, "rel");
                    if (href != null && !href.isEmpty()
                            && (rel == null || rel.isEmpty() || rel.equals("alternate"))) {
                        if (p.link.isEmpty()) p.link = href.trim();
                    }
                    skip(x);
                    break;
                }
                case "summary":
                case "content": {
                    String d = readText(x);
                    if (p.description.isEmpty()) p.description = snippet(d);
                    break;
                }
                case "updated":
                case "published": {
                    long t = parseDate(readText(x).trim());
                    if (t > 0 && (p.pubDate == 0 || tag.equals("published"))) p.pubDate = t;
                    break;
                }
                default: skip(x); break;
            }
        }
        if (p.guid.isEmpty()) p.guid = p.link;
        if (p.pubDate == 0) p.pubDate = System.currentTimeMillis();
        return p;
    }

    private static String readText(XmlPullParser x) throws Exception {
        StringBuilder sb = new StringBuilder();
        int ev;
        int depth = x.getDepth();
        while ((ev = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.TEXT || ev == XmlPullParser.CDSECT) sb.append(x.getText());
            else if (ev == XmlPullParser.END_TAG && x.getDepth() == depth) break;
            else if (ev == XmlPullParser.START_TAG) skip(x);
        }
        return sb.toString();
    }

    private static void skip(XmlPullParser x) throws Exception {
        int depth = x.getDepth();
        int ev;
        while ((ev = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG && x.getDepth() == depth) break;
        }
    }

    private static String clean(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String snippet(String html) {
        String t = html.replaceAll("<[^>]*>", " ");
        t = t.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
             .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");
        t = clean(t);
        return t.length() > 220 ? t.substring(0, 220) + "…" : t;
    }

    private static final String[] DATE_FORMATS = {
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd MMM yy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd HH:mm:ss",
    };

    public static long parseDate(String s) {
        if (s == null || s.isEmpty()) return 0;
        for (String f : DATE_FORMATS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(f, Locale.US);
                sdf.setLenient(true);
                if (f.endsWith("'Z'")) sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = sdf.parse(s);
                if (d != null) return d.getTime();
            } catch (ParseException ignored) { }
        }
        return 0;
    }
}
