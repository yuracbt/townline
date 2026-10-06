package com.chobotok.townline;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Discover: suggested local feeds, event pages, saved links. Opens in your browser. */
public class DiscoverActivity extends Activity {

    private NewsDbHelper db;
    private Prefs prefs;
    private LinkAdapter eventsAdapter;
    private PostAdapter savedAdapter;
    private SuggestAdapter suggestAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discover);
        setTitle("Discover");

        db = new NewsDbHelper(this);
        prefs = new Prefs(this);

        ListView suggestList = findViewById(R.id.suggestList);
        ListView eventsList = findViewById(R.id.eventsList);
        ListView savedList = findViewById(R.id.savedList);
        suggestAdapter = new SuggestAdapter();
        eventsAdapter = new LinkAdapter();
        savedAdapter = new PostAdapter();
        suggestList.setAdapter(suggestAdapter);
        eventsList.setAdapter(eventsAdapter);
        savedList.setAdapter(savedAdapter);

        ((TextView) findViewById(R.id.suggestSubtitle)).setText(
                "Checking which local feeds work for " + prefs.getTown() + "…");

        findViewById(R.id.btnAddEvent).setOnClickListener(v ->
                showAddDialog("Add event page", "https://…"));

        findViewById(R.id.btnScanNearby).setOnClickListener(v -> scanNearby());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
        checkSuggestedFeeds();
    }

    private void reload() {
        eventsAdapter.links = db.getLinks("events");
        savedAdapter.posts = db.getSavedItems();
        eventsAdapter.notifyDataSetChanged();
        savedAdapter.notifyDataSetChanged();
    }

    /** Verify directory feeds for this town in the background, then show the working ones. */
    private void checkSuggestedFeeds() {
        suggestAdapter.state = SuggestAdapter.CHECKING;
        suggestAdapter.entries = new ArrayList<>();
        suggestAdapter.notifyDataSetChanged();
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
            final List<FeedDirectory.Entry> result = working;
            runOnUiThread(() -> {
                suggestAdapter.state = SuggestAdapter.DONE;
                suggestAdapter.entries = result;
                suggestAdapter.notifyDataSetChanged();
                ((TextView) findViewById(R.id.suggestSubtitle)).setText(result.isEmpty()
                        ? "No new feeds found for " + prefs.getTown()
                                + " — the directory grows with each release."
                        : result.size() + " working local feed(s) for " + prefs.getTown() + ":");
            });
        }).start();
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
                    reload();
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
            final List<FeedDirectory.Entry> result = working;
            String cur = prefs.getTown();
            final boolean sameTown =
                    town.equalsIgnoreCase(cur.split(",")[0].trim()) || cur.equalsIgnoreCase(town);
            runOnUiThread(() -> buildNearbyDialog(town, result, sameTown));
        }).start();
    }

    private void buildNearbyDialog(String town, List<FeedDirectory.Entry> feeds,
                                   boolean sameTown) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);

        TextView head = new TextView(this);
        head.setText("You appear to be near " + town + ".");
        head.setTextSize(15);
        layout.addView(head);

        List<android.widget.CheckBox> boxes = new ArrayList<>();
        for (FeedDirectory.Entry e : feeds) {
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(e.name);
            cb.setChecked(true);
            cb.setTag(e);
            layout.addView(cb);
            boxes.add(cb);
        }
        android.widget.CheckBox townBox = null;
        if (!sameTown) {
            townBox = new android.widget.CheckBox(this);
            townBox.setText("Use " + town + " as my town");
            townBox.setChecked(true);
            layout.addView(townBox);
        }
        if (feeds.isEmpty() && sameTown) {
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
                    boolean switched = switchBox != null && switchBox.isChecked();
                    if (switched) prefs.setTown(town);
                    if (added > 0 || switched) {
                        SyncJobService.syncNow(this);
                        Toast.makeText(this,
                                "Added " + added + " feed(s) — scanning now",
                                Toast.LENGTH_SHORT).show();
                    }
                    reload();
                    checkSuggestedFeeds();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class SuggestAdapter extends BaseAdapter {
        static final int CHECKING = 0;
        static final int DONE = 1;
        int state = CHECKING;
        List<FeedDirectory.Entry> entries = new ArrayList<>();
        final List<String> added = new ArrayList<>();

        @Override public int getCount() { return state == CHECKING ? 0 : entries.size(); }
        @Override public Object getItem(int p) { return entries.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_suggest, parent, false);
            }
            final FeedDirectory.Entry e = entries.get(position);
            ((TextView) convertView.findViewById(R.id.suggestName)).setText(e.name);
            ((TextView) convertView.findViewById(R.id.suggestUrl)).setText(e.url);
            Button add = convertView.findViewById(R.id.suggestAdd);
            boolean done = added.contains(norm(e.url));
            add.setText(done ? "✓" : "Add");
            add.setEnabled(!done);
            add.setOnClickListener(v -> {
                db.addSource(e.name, e.url, true, false);
                added.add(norm(e.url));
                notifyDataSetChanged();
                Toast.makeText(DiscoverActivity.this,
                        "Added — it will be scanned with the next sync", Toast.LENGTH_SHORT).show();
            });
            return convertView;
        }
    }

    private class LinkAdapter extends BaseAdapter {
        List<Link> links = new ArrayList<>();

        @Override public int getCount() { return links.size(); }
        @Override public Object getItem(int p) { return links.get(p); }
        @Override public long getItemId(int p) { return links.get(p).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_link, parent, false);
            }
            final Link l = links.get(position);
            ((TextView) convertView.findViewById(R.id.linkName)).setText(l.name);
            ((TextView) convertView.findViewById(R.id.linkUrl)).setText(l.url);
            convertView.findViewById(R.id.linkDelete).setOnClickListener(v -> {
                db.deleteLink(l.id);
                reload();
            });
            convertView.setOnClickListener(v -> openExternal(l.url));
            return convertView;
        }
    }

    /** Links saved via Share -> TownLine. Tap to open, X to remove. */
    private class PostAdapter extends BaseAdapter {
        List<NewsItem> posts = new ArrayList<>();

        @Override public int getCount() { return posts.size(); }
        @Override public Object getItem(int p) { return posts.get(p); }
        @Override public long getItemId(int p) { return posts.get(p).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_link, parent, false);
            }
            final NewsItem n = posts.get(position);
            ((TextView) convertView.findViewById(R.id.linkName)).setText(n.title);
            ((TextView) convertView.findViewById(R.id.linkUrl)).setText(n.link);
            convertView.findViewById(R.id.linkDelete).setOnClickListener(v -> {
                db.deleteItem(n.id);
                reload();
                Toast.makeText(DiscoverActivity.this, "Removed", Toast.LENGTH_SHORT).show();
            });
            convertView.setOnClickListener(v -> openExternal(n.link));
            return convertView;
        }
    }
}
