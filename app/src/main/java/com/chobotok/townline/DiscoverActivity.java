package com.chobotok.townline;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Discover: suggested local feeds, event pages, saved links. Opens in your browser.
 * Lists are plain LinearLayouts (not nested ListViews) so the whole page scrolls.
 */
public class DiscoverActivity extends Activity {

    private NewsDbHelper db;
    private Prefs prefs;
    private LinearLayout suggestBox;
    private LinearLayout eventsBox;
    private LinearLayout savedBox;
    private final Set<String> suggestAdded = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discover);
        setTitle("Discover");

        db = new NewsDbHelper(this);
        prefs = new Prefs(this);

        suggestBox = findViewById(R.id.suggestList);
        eventsBox = findViewById(R.id.eventsList);
        savedBox = findViewById(R.id.savedList);

        ((TextView) findViewById(R.id.suggestSubtitle)).setText(
                "Checking which local feeds work for " + prefs.getTown() + "…");

        findViewById(R.id.btnAddEvent).setOnClickListener(v ->
                showAddDialog("Add event page", "https://…"));

        findViewById(R.id.btnScanNearby).setOnClickListener(v -> scanNearby());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
        checkSuggestedFeeds();
    }

    private void reload() {
        renderEvents();
        renderSaved();
    }

    // ---------- suggested feeds ----------

    /** Verify directory feeds for this town in the background, then show the working ones. */
    private void checkSuggestedFeeds() {
        suggestBox.removeAllViews();
        new Thread(() -> {
            List<FeedDirectory.Entry> candidates =
                    FeedDirectory.suggestionsFor(DiscoverActivity.this, prefs.getTown());
            // drop ones already added as sources
            List<String> have = new ArrayList<>();
            for (Source s : db.getSources()) have.add(norm(s.url));
            List<FeedDirectory.Entry> working = new ArrayList<>();
            for (FeedDirectory.Entry e : candidates) {
                if (have.contains(norm(e.url))) continue;
                if (FeedDirectory.isWorkingFeed(e.url)) working.add(e);
            }
            // event places for this town that aren't already added as links
            List<String> haveLinks = new ArrayList<>();
            for (Link l : db.getLinks("events")) haveLinks.add(norm(l.url));
            for (FeedDirectory.Entry p : FeedDirectory.placesFor(
                    DiscoverActivity.this, prefs.getTown())) {
                if (!haveLinks.contains(norm(p.url))) working.add(p);
            }
            final List<FeedDirectory.Entry> result = working;
            final int total = FeedDirectory.totalEntries(DiscoverActivity.this);
            runOnUiThread(() -> {
                renderSuggestions(result);
                TextView sub = findViewById(R.id.suggestSubtitle);
                if (total == 0) {
                    sub.setText("Feed directory couldn't be loaded — please reinstall the app.");
                } else if (result.isEmpty()) {
                    sub.setText("No new feeds found for " + prefs.getTown()
                            + " — the directory grows with each release.");
                } else {
                    sub.setText(result.size() + " suggestion(s) for " + prefs.getTown() + ":");
                }
            });
        }).start();
    }

    private void renderSuggestions(List<FeedDirectory.Entry> entries) {
        suggestBox.removeAllViews();
        for (FeedDirectory.Entry e : entries) {
            View row = getLayoutInflater().inflate(R.layout.item_suggest, suggestBox, false);
            ((TextView) row.findViewById(R.id.suggestName)).setText(e.name);
            ((TextView) row.findViewById(R.id.suggestUrl)).setText(e.url);
            Button add = row.findViewById(R.id.suggestAdd);
            boolean done = suggestAdded.contains(norm(e.url));
            add.setText(done ? "✓" : "Add");
            add.setEnabled(!done);
            add.setOnClickListener(v -> {
                if ("place".equals(e.kind)) {
                    db.addLink("events", e.name, e.url);
                } else {
                    db.addSource(e.name, e.url, true, false);
                    SyncJobService.syncNow(DiscoverActivity.this);
                }
                suggestAdded.add(norm(e.url));
                add.setText("✓");
                add.setEnabled(false);
                renderEvents();
                Toast.makeText(DiscoverActivity.this,
                        "place".equals(e.kind) ? "Added to Events & places"
                                : "Added — scanning now",
                        Toast.LENGTH_SHORT).show();
            });
            suggestBox.addView(row);
        }
    }

    // ---------- event links ----------

    private void renderEvents() {
        eventsBox.removeAllViews();
        for (Link l : db.getLinks("events")) {
            View row = getLayoutInflater().inflate(R.layout.item_link, eventsBox, false);
            ((TextView) row.findViewById(R.id.linkName)).setText(l.name);
            ((TextView) row.findViewById(R.id.linkUrl)).setText(l.url);
            row.findViewById(R.id.linkDelete).setOnClickListener(v -> {
                db.deleteLink(l.id);
                renderEvents();
            });
            row.setOnClickListener(v -> openExternal(l.url));
            eventsBox.addView(row);
        }
    }

    // ---------- saved links ----------

    /** Links saved via Share -> TownLine. Tap to open, X to remove. */
    private void renderSaved() {
        savedBox.removeAllViews();
        for (NewsItem n : db.getSavedItems()) {
            View row = getLayoutInflater().inflate(R.layout.item_link, savedBox, false);
            ((TextView) row.findViewById(R.id.linkName)).setText(n.title);
            ((TextView) row.findViewById(R.id.linkUrl)).setText(n.link);
            row.findViewById(R.id.linkDelete).setOnClickListener(v -> {
                db.deleteItem(n.id);
                renderSaved();
                Toast.makeText(DiscoverActivity.this, "Removed", Toast.LENGTH_SHORT).show();
            });
            row.setOnClickListener(v -> openExternal(n.link));
            savedBox.addView(row);
        }
    }

    private static String norm(String u) {
        if (u == null) return "";
        return u.trim().toLowerCase().replaceAll("/$", "");
    }

    private void showAddDialog(String title, String urlHint) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);
        final EditText nameInput = new EditText(this);
        nameInput.setHint("Name");
        final EditText urlInput = new EditText(this);
        urlInput.setHint(urlHint);
        urlInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(nameInput);
        layout.addView(urlInput);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(layout)
                .setPositiveButton("Add", (d, w) -> {
                    String name = nameInput.getText().toString().trim();
                    String url = urlInput.getText().toString().trim();
                    if (name.isEmpty()) name = url;
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        Toast.makeText(this, "Link must start with http(s)://", Toast.LENGTH_LONG).show();
                        return;
                    }
                    db.addLink("events", name, url);
                    renderEvents();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Can't open link", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- Scan near me ----------

    private static final int REQ_LOC = 41;

    private void scanNearby() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
            return;
        }
        doLocationScan();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOC) {
            if (grantResults.length > 0
                    && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                doLocationScan();
            } else {
                Toast.makeText(this, "Location denied — the town list below still works",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void doLocationScan() {
        Toast.makeText(this, "Locating…", Toast.LENGTH_SHORT).show();
        try {
            android.location.LocationManager lm =
                    (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
            android.location.Location best = null;
            for (String p : lm.getProviders(true)) {
                try {
                    android.location.Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (SecurityException ignored) { }
            }
            if (best != null && System.currentTimeMillis() - best.getTime() < 10 * 60 * 1000) {
                onLocationFound(best);
                return;
            }
            if (!lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                if (best != null) {
                    onLocationFound(best);
                } else {
                    Toast.makeText(this, "Location is off — enable it or use the list below",
                            Toast.LENGTH_LONG).show();
                }
                return;
            }
            final boolean[] done = {false};
            android.location.LocationListener ll = new android.location.LocationListener() {
                @Override
                public void onLocationChanged(android.location.Location l) {
                    if (done[0]) return;
                    done[0] = true;
                    try { lm.removeUpdates(this); } catch (Exception ignored) { }
                    onLocationFound(l);
                }
                @Override
                public void onStatusChanged(String p, int s, android.os.Bundle b) { }
                @Override
                public void onProviderEnabled(String p) { }
                @Override
                public void onProviderDisabled(String p) { }
            };
            lm.requestSingleUpdate(
                    android.location.LocationManager.NETWORK_PROVIDER, ll, null);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (done[0]) return;
                done[0] = true;
                try { lm.removeUpdates(ll); } catch (Exception ignored) { }
                android.location.Location l = null;
                try {
                    l = lm.getLastKnownLocation(
                            android.location.LocationManager.NETWORK_PROVIDER);
                } catch (SecurityException ignored) { }
                if (l != null) {
                    onLocationFound(l);
                } else {
                    Toast.makeText(DiscoverActivity.this,
                            "Couldn't get a location fix — try again outside",
                            Toast.LENGTH_LONG).show();
                }
            }, 20000);
        } catch (Exception e) {
            Toast.makeText(this, "Location unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    private void onLocationFound(android.location.Location loc) {
        new Thread(() -> {
            String town = null;
            try {
                android.location.Geocoder g = new android.location.Geocoder(
                        DiscoverActivity.this, java.util.Locale.getDefault());
                List<android.location.Address> a =
                        g.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
                if (a != null && !a.isEmpty()) {
                    android.location.Address ad = a.get(0);
                    town = ad.getLocality();
                    if (town == null) town = ad.getSubAdminArea();
                    if (town == null) town = ad.getAdminArea();
                }
            } catch (Exception ignored) { }
            final String found = town;
            runOnUiThread(() -> {
                if (found == null || found.isEmpty()) {
                    Toast.makeText(this,
                            "Couldn't determine your town — the list below still works",
                            Toast.LENGTH_LONG).show();
                } else {
                    showNearbyDialog(found);
                }
            });
        }).start();
    }

    private void showNearbyDialog(final String town) {
        Toast.makeText(this, "Checking feeds near " + town + "…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<FeedDirectory.Entry> candidates =
                    FeedDirectory.suggestionsFor(DiscoverActivity.this, town);
            List<String> have = new ArrayList<>();
            for (Source s : db.getSources()) have.add(norm(s.url));
            List<FeedDirectory.Entry> working = new ArrayList<>();
            for (FeedDirectory.Entry e : candidates) {
                if (have.contains(norm(e.url))) continue;
                if (FeedDirectory.isWorkingFeed(e.url)) working.add(e);
            }
            List<String> haveLinks = new ArrayList<>();
            for (Link l : db.getLinks("events")) haveLinks.add(norm(l.url));
            List<FeedDirectory.Entry> places = new ArrayList<>();
            for (FeedDirectory.Entry p : FeedDirectory.placesFor(
                    DiscoverActivity.this, town)) {
                if (!haveLinks.contains(norm(p.url))) places.add(p);
            }
            final List<FeedDirectory.Entry> result = working;
            final List<FeedDirectory.Entry> resultPlaces = places;
            String cur = prefs.getTown();
            final boolean sameTown =
                    town.equalsIgnoreCase(cur.split(",")[0].trim()) || cur.equalsIgnoreCase(town);
            runOnUiThread(() -> buildNearbyDialog(town, result, resultPlaces, sameTown));
        }).start();
    }

    private void buildNearbyDialog(String town, List<FeedDirectory.Entry> feeds,
                                   List<FeedDirectory.Entry> places, boolean sameTown) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);

        TextView head = new TextView(this);
        head.setText("You appear to be near " + town + ".");
        head.setTextSize(15);
        layout.addView(head);

        List<android.widget.CheckBox> boxes = new ArrayList<>();
        if (!feeds.isEmpty()) {
            TextView fh = new TextView(this);
            fh.setText("News feeds");
            fh.setTextSize(14);
            fh.setTypeface(null, android.graphics.Typeface.BOLD);
            fh.setPadding(0, 16, 0, 4);
            layout.addView(fh);
        }
        for (FeedDirectory.Entry e : feeds) {
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(e.name);
            cb.setChecked(true);
            cb.setTag(e);
            layout.addView(cb);
            boxes.add(cb);
        }
        List<android.widget.CheckBox> placeBoxes = new ArrayList<>();
        if (!places.isEmpty()) {
            TextView ph = new TextView(this);
            ph.setText("Event pages & places");
            ph.setTextSize(14);
            ph.setTypeface(null, android.graphics.Typeface.BOLD);
            ph.setPadding(0, 16, 0, 4);
            layout.addView(ph);
        }
        for (FeedDirectory.Entry e : places) {
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(e.name);
            cb.setChecked(true);
            cb.setTag(e);
            layout.addView(cb);
            placeBoxes.add(cb);
        }
        android.widget.CheckBox townBox = null;
        if (!sameTown) {
            townBox = new android.widget.CheckBox(this);
            townBox.setText("Use " + town + " as my town");
            townBox.setChecked(true);
            layout.addView(townBox);
        }
        if (feeds.isEmpty() && places.isEmpty() && sameTown) {
            TextView t = new TextView(this);
            t.setText("No new feeds found nearby — your town is already covered.");
            layout.addView(t);
        }
        final android.widget.CheckBox switchBox = townBox;

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(layout);

        new AlertDialog.Builder(this)
                .setTitle("News near " + town)
                .setView(sv)
                .setPositiveButton("Add selected", (d, w) -> {
                    int added = 0;
                    for (android.widget.CheckBox cb : boxes) {
                        if (cb.isChecked()) {
                            FeedDirectory.Entry e = (FeedDirectory.Entry) cb.getTag();
                            db.addSource(e.name, e.url, true, false);
                            added++;
                        }
                    }
                    for (android.widget.CheckBox cb : placeBoxes) {
                        if (cb.isChecked()) {
                            FeedDirectory.Entry e = (FeedDirectory.Entry) cb.getTag();
                            db.addLink("events", e.name, e.url);
                            added++;
                        }
                    }
                    boolean switched = switchBox != null && switchBox.isChecked();
                    if (switched) prefs.setTown(town);
                    if (added > 0 || switched) {
                        SyncJobService.syncNow(this);
                        Toast.makeText(this,
                                "Added " + added + " item(s) — scanning now",
                                Toast.LENGTH_SHORT).show();
                    }
                    reload();
                    checkSuggestedFeeds();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
