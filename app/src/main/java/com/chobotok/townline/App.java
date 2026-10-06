package com.chobotok.townline;

import android.app.Application;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs prefs = new Prefs(this);
        if (!prefs.isSeeded()) {
            seedSources();
            prefs.setSeeded();
        }
        NewsDbHelper db = new NewsDbHelper(this);
        if (db.getLinksCount() == 0) {
            seedEventLinks(db);
        }
        // v1.4: "Facebook" category renamed to the neutral "Saved"
        db.getWritableDatabase().execSQL(
                "UPDATE items SET category='Saved' WHERE category='Facebook'");
        // one-time: categorize stories saved before v1.2
        if (!prefs.isRecatV3()) {
            java.util.List<NewsItem> uncat = db.getUncategorized(2000);
            for (NewsItem n : uncat) {
                db.setCategory(n.id, Categorizer.categorize(n.sourceName, n.title, n.description));
            }
            prefs.setRecatV3();
        }
        db.close();
        SyncJobService.schedule(this);
    }

    private void seedSources() {
        NewsDbHelper db = new NewsDbHelper(this);
        db.addSource("DiscoverAirdrie — Local News",
                "https://www.discoverairdrie.com/rss/local-news", true, false);
        db.addSource("DiscoverAirdrie — Community",
                "https://www.discoverairdrie.com/rss/community", true, false);
        db.addSource("Google News — my town", "", true, true); // URL built from town
        db.addSource("Google News — Calgary",
                "https://news.google.com/rss/search?q=Calgary&hl=en-CA&gl=CA&ceid=CA%3Aen",
                true, false);
        db.addSource("Calgary Herald", "https://calgaryherald.com/feed", true, false);
        db.close();
    }

    private void seedEventLinks(NewsDbHelper db) {
        db.addLink("events", "Airdrie Chamber — Business Events",
                "https://www.airdriechamber.ab.ca/events/");
        db.addLink("events", "City of Airdrie — Community Calendar",
                "http://www.airdrie.ca/index.cfm?serviceID=667");
        db.addLink("events", "Airdrie Public Library — Programs",
                "https://www.yourapl.ca/Programs-and-Events/apl-program-brochure");
        db.addLink("events", "Airdrie Public Library — Event Bookings",
                "https://bookings.yourapl.ca/");
        db.addLink("events", "Eventbrite — Airdrie",
                "https://www.eventbrite.ca/d/canada--airdrie/events/");
    }
}
