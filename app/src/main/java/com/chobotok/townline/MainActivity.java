package com.chobotok.townline;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The news line: newest stories first, tap to read. */
public class MainActivity extends Activity {

    private NewsDbHelper db;
    private Prefs prefs;
    private FeedAdapter adapter;
    private ListView list;
    private TextView lastSync;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = new NewsDbHelper(this);
        prefs = new Prefs(this);

        list = findViewById(R.id.feedList);
        lastSync = findViewById(R.id.lastSync);
        Button btnRefresh = findViewById(R.id.btnRefresh);
        Button btnSettings = findViewById(R.id.btnSettings);

        adapter = new FeedAdapter();
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.emptyView));

        list.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            NewsItem item = adapter.items.get(position);
            db.markRead(item.id);
            item.isNew = false;
            adapter.notifyDataSetChanged();
            Intent i = new Intent(MainActivity.this, ArticleActivity.class);
            i.putExtra("url", item.link);
            i.putExtra("title", item.title);
            startActivity(i);
        });

        btnRefresh.setOnClickListener(v -> {
            SyncJobService.syncNow(MainActivity.this);
            Toast.makeText(this, "Scanning…", Toast.LENGTH_SHORT).show();
            handler.postDelayed(this::refresh, 8000);
        });

        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // first ever open: kick a scan if the feed is empty
        if (db.getItems(1).isEmpty()) {
            SyncJobService.syncNow(this);
            handler.postDelayed(this::refresh, 8000);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        adapter.items = db.getItems(300);
        adapter.notifyDataSetChanged();
        long ls = prefs.getLastSync();
        lastSync.setText(ls == 0 ? "Last scan: —"
                : "Last scan: " + relTime(ls) + " • every " + prefs.getIntervalHours() + "h");
    }

    static String relTime(long t) {
        long d = System.currentTimeMillis() - t;
        if (d < 0) d = 0;
        long m = d / 60000;
        if (m < 1) return "just now";
        if (m < 60) return m + "m ago";
        long h = m / 60;
        if (h < 24) return h + "h ago";
        long days = h / 24;
        if (days < 7) return days + "d ago";
        return new java.text.SimpleDateFormat("MMM d", Locale.US).format(new Date(t));
    }

    private class FeedAdapter extends BaseAdapter {
        List<NewsItem> items = new java.util.ArrayList<>();

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int p) { return items.get(p); }
        @Override public long getItemId(int p) { return items.get(p).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_news, parent, false);
            }
            NewsItem n = items.get(position);
            TextView title = convertView.findViewById(R.id.itemTitle);
            TextView meta = convertView.findViewById(R.id.itemMeta);
            View dot = convertView.findViewById(R.id.newDot);
            title.setText(n.title);
            title.setTypeface(null, n.isNew ? Typeface.BOLD : Typeface.NORMAL);
            String src = n.sourceName == null ? "" : n.sourceName;
            meta.setText(src + (src.isEmpty() ? "" : " • ") + relTime(n.pubDate));
            dot.setVisibility(n.isNew ? View.VISIBLE : View.INVISIBLE);
            return convertView;
        }
    }
}
