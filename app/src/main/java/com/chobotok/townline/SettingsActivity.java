package com.chobotok.townline;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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
    private LinearLayout sourceBox;

    private static final String[] INTERVAL_LABELS =
            {"Every 5 minutes", "Every 15 minutes", "Every 30 minutes",
             "Every hour", "Every 2 hours", "Every 4 hours",
             "Every 8 hours", "Every 12 hours", "Every day"};
    private static final int[] INTERVAL_VALUES = {5, 15, 30, 60, 120, 240, 480, 720, 1440};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        setTitle("Settings");

        db = new NewsDbHelper(this);
        prefs = new Prefs(this);

        Spinner spinner = findViewById(R.id.intervalSpinner);
        ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, INTERVAL_LABELS);
        spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinAdapter);
        spinner.setSelection(indexOf(prefs.getIntervalMinutes()));
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            boolean first = true;
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (first) { first = false; return; }
                prefs.setIntervalMinutes(INTERVAL_VALUES[pos]);
                SyncJobService.schedule(SettingsActivity.this);
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

        findViewById(R.id.btnBatterySettings).setOnClickListener(v -> {
            try {
                startActivity(new Intent(
                        android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Open Settings → Apps → TownLine → Battery → Unrestricted",
                        Toast.LENGTH_LONG).show();
            }
        });

        LinearLayout sourceBox = findViewById(R.id.sourceList);
        this.sourceBox = sourceBox;

        findViewById(R.id.btnAddSource).setOnClickListener(v -> showAddSourceDialog());

    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadSources();
        refreshBatteryWarn();
    }

    /** Shows the battery-optimization warning only when background work may be throttled. */
    private void refreshBatteryWarn() {
        boolean ignoring = true;
        try {
            android.os.PowerManager pm =
                    (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) ignoring = pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Exception ignored) { }
        View warn = findViewById(R.id.batteryWarn);
        warn.setVisibility(ignoring ? View.GONE : View.VISIBLE);
    }

    private void reloadSources() {
        renderSources();
    }

    private void renderSources() {
        sourceBox.removeAllViews();
        for (Source s : db.getSources()) {
            View row = getLayoutInflater().inflate(R.layout.item_source, sourceBox, false);
            TextView name = row.findViewById(R.id.sourceName);
            TextView meta = row.findViewById(R.id.sourceMeta);
            CheckBox enabled = row.findViewById(R.id.sourceEnabled);
            Button delete = row.findViewById(R.id.sourceDelete);

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
                            renderSources();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
            sourceBox.addView(row);
        }
    }

    private int indexOf(int minutes) {
        for (int i = 0; i < INTERVAL_VALUES.length; i++)
            if (INTERVAL_VALUES[i] == minutes) return i;
        return 5; // default: every 4 hours
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
}
