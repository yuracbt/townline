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

import java.util.List;

/** Quick links: your Facebook groups + local event pages. Opens in your browser. */
public class DiscoverActivity extends Activity {

    private NewsDbHelper db;
    private LinkAdapter fbAdapter;
    private LinkAdapter eventsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discover);
        setTitle("Discover");

        db = new NewsDbHelper(this);

        ListView fbList = findViewById(R.id.fbList);
        ListView eventsList = findViewById(R.id.eventsList);
        fbAdapter = new LinkAdapter("fb");
        eventsAdapter = new LinkAdapter("events");
        fbList.setAdapter(fbAdapter);
        eventsList.setAdapter(eventsAdapter);

        findViewById(R.id.btnAddFb).setOnClickListener(v ->
                showAddDialog("fb", "Facebook group", "Paste the group's link from Facebook"));
        findViewById(R.id.btnAddEvent).setOnClickListener(v ->
                showAddDialog("events", "Event page", "https://…"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        fbAdapter.links = db.getLinks("fb");
        eventsAdapter.links = db.getLinks("events");
        fbAdapter.notifyDataSetChanged();
        eventsAdapter.notifyDataSetChanged();
    }

    private void showAddDialog(final String kind, String title, String urlHint) {
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
                    db.addLink(kind, name, url);
                    reload();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class LinkAdapter extends BaseAdapter {
        final String kind;
        List<Link> links = new java.util.ArrayList<>();

        LinkAdapter(String kind) { this.kind = kind; }

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
            convertView.setOnClickListener(v -> {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(l.url));
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(DiscoverActivity.this, "Can't open link", Toast.LENGTH_SHORT).show();
                }
            });
            return convertView;
        }
    }
}
