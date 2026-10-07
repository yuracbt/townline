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

        findViewById(R.id.btnExportConfig).setOnClickListener(v -> exportConfig());
        findViewById(R.id.btnImportConfig).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("text/*");
            try {
                startActivityForResult(i, REQ_IMPORT);
            } catch (Exception e) {
                Toast.makeText(this, "No file picker found", Toast.LENGTH_SHORT).show();
            }
        });

    }

    private static final int REQ_IMPORT = 41;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            try (java.io.InputStream in =
                         getContentResolver().openInputStream(data.getData());
                 java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                ConfigBackup.Result r =
                        ConfigBackup.importCsv(bos.toString("UTF-8"), prefs, db);
                if (r.settingsApplied) SyncJobService.schedule(this);
                int dupes = r.feedsSkipped + r.eventsSkipped;
                Toast.makeText(this,
                        "Imported " + r.feedsAdded + " feed(s), " + r.eventsAdded
                                + " event page(s)"
                                + (dupes > 0 ? " — " + dupes + " already there" : "")
                                + (r.settingsApplied ? " — settings restored" : ""),
                        Toast.LENGTH_LONG).show();
                reloadSources();
            } catch (Exception e) {
                Toast.makeText(this, "Import failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Writes the configuration CSV to Downloads (or app storage on old Android). */
    private void exportConfig() {
        String csv = ConfigBackup.exportCsv(prefs, db);
        String name = "townline-config.csv";
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/csv");
                v.put(android.provider.MediaStore.Downloads.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS);
                android.net.Uri uri = getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new Exception("could not create file");
                try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                    os.write(csv.getBytes("UTF-8"));
                }
                Toast.makeText(this, "Saved to Downloads/" + name, Toast.LENGTH_LONG).show();
            } else {
                java.io.File f = new java.io.File(getExternalFilesDir(null), name);
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f)) {
                    fos.write(csv.getBytes("UTF-8"));
                }
                Toast.makeText(this, "Saved to " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
