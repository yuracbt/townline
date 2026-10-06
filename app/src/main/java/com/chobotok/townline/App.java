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
}
