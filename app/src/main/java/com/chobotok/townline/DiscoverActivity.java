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
