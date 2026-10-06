package com.chobotok.townline;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Scan interval, notifications, and the source list (add your own RSS/Atom feeds). */
public class SettingsActivity extends Activity {

    private NewsDbHelper db;
    private Prefs prefs;
    private SourceAdapter adapter;

    private static final String[] INTERVAL_LABELS =
            {"Every hour", "Every 2 hours", "Every 4 hours", "Every 8 hours", "Every 12 hours", "Every day"};
    private static final int[] INTERVAL_VALUES = {1, 2, 4, 8, 12, 24};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle("Settings");

        db = new NewsDbHelper(this);
        prefs = new Prefs(this);

        Spinner spinner = findViewById(R.id.intervalSpinner);
        ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, INTERVAL_LABELS);
        spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinAdapter);
        spinner.setSelection(indexOf(prefs.getIntervalHours()));
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            boolean first = true;
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (first) { first = false; return; }
                prefs.setIntervalHours(INTERVAL_VALUES[pos]);
                SyncJobService.schedule(SettingsActivity.this);
                refreshAbout();
                Toast.makeText(SettingsActivity.this, "Scan interval updated", Toast.LENGTH_SHORT).show();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        Switch notifySwitch = findViewById(R.id.notifySwitch);
        notifySwitch.setChecked(prefs.isNotifyEnabled());
        notifySwitch.setOnCheckedChangeListener((b, checked) -> prefs.setNotifyEnabled(checked));

        findViewById(R.id.btnSyncNow).setOnClickListener(v -> {
            SyncJobService.syncNow(SettingsActivity.this);
            Toast.makeText(this, "Scanning…", Toast.LENGTH_SHORT).show();
        });

        ListView sourceList = findViewById(R.id.sourceList);
        adapter = new SourceAdapter();
        sourceList.setAdapter(adapter);

        findViewById(R.id.btnAddSource).setOnClickListener(v -> showAddSourceDialog());

        refreshAbout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadSources();
    }

    private void reloadSources() {
        adapter.sources = db.getSources();
        adapter.notifyDataSetChanged();
    }

    private int indexOf(int hours) {
        for (int i = 0; i < INTERVAL_VALUES.length; i++)
            if (INTERVAL_VALUES[i] == hours) return i;
        return 2;
    }

    private void refreshAbout() {
        TextView about = findViewById(R.id.aboutText);
        about.setText("TownLine v1.0 — scans run every " + prefs.getIntervalHours()
                + "h in the background and notify you about new stories.\n" +
                "Facebook groups can't be scanned automatically (Meta doesn't allow it) — " +
                "add any public RSS/Atom feed URL above instead.");
    }

    private void showAddSourceDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);
        final EditText nameInput = new EditText(this);
        nameInput.setHint("Name, e.g. Airdrie Library");
        final EditText urlInput = new EditText(this);
        urlInput.setHint("Feed URL, e.g. https://…/feed");
        urlInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(nameInput);
        layout.addView(urlInput);

        new AlertDialog.Builder(this)
                .setTitle("Add feed")
                .setView(layout)
                .setPositiveButton("Add", (d, w) -> {
                    String name = nameInput.getText().toString().trim();
                    String url = urlInput.getText().toString().trim();
                    if (name.isEmpty()) name = url;
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        Toast.makeText(this, "URL must start with http(s)://", Toast.LENGTH_LONG).show();
                        return;
                    }
                    db.addSource(name, url, true, false);
                    reloadSources();
                    SyncJobService.syncNow(this);
                    Toast.makeText(this, "Added — scanning now", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class SourceAdapter extends BaseAdapter {
        List<Source> sources = new java.util.ArrayList<>();

        @Override public int getCount() { return sources.size(); }
        @Override public Object getItem(int p) { return sources.get(p); }
        @Override public long getItemId(int p) { return sources.get(p).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_source, parent, false);
            }
            Source s = sources.get(position);
            TextView name = convertView.findViewById(R.id.sourceName);
            TextView meta = convertView.findViewById(R.id.sourceMeta);
            CheckBox enabled = convertView.findViewById(R.id.sourceEnabled);
            Button delete = convertView.findViewById(R.id.sourceDelete);

            name.setText(s.name);
            StringBuilder m = new StringBuilder();
            if (s.lastError != null) m.append("Error: ").append(s.lastError);
            else if (s.lastSync == 0) m.append("Not scanned yet");
            else m.append(s.lastCount).append(" stories • ")
                    .append(MainActivity.relTime(s.lastSync));
            meta.setText(m.toString());

            enabled.setOnCheckedChangeListener(null);
            enabled.setChecked(s.enabled);
            enabled.setOnCheckedChangeListener((b, checked) -> {
                s.enabled = checked;
                db.updateSource(s);
            });

            delete.setOnClickListener(v -> {
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Remove source?")
                        .setMessage(s.name)
                        .setPositiveButton("Remove", (d, w) -> {
                            db.deleteSource(s.id);
                            reloadSources();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
            return convertView;
        }
    }
}
