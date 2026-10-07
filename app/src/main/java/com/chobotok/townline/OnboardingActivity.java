package com.chobotok.townline;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/** First-run: town, notification permission, what needs a login (nothing). */
public class OnboardingActivity extends Activity {

    private static final int REQ_NOTIF = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs prefs = new Prefs(this);
        if (!prefs.isFirstRun()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_onboarding);

        EditText townInput = findViewById(R.id.townInput);
        townInput.setText(prefs.getTown());

        Button btnStart = findViewById(R.id.btnStart);
        btnStart.setOnClickListener(v -> {
            String town = townInput.getText().toString().trim();
            if (!town.isEmpty()) prefs.setTown(town);

            prefs.setFirstRunDone();
            SyncJobService.schedule(this);
            SyncJobService.syncNow(this);
            Toast.makeText(this, "Scanning your town now…", Toast.LENGTH_SHORT).show();

            if (NotifPerms.canNotify(this)) {
                proceed();
            } else {
                // Ask; if the user says no, offer the jump into Android settings.
                NotifPerms.request(this, REQ_NOTIF);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                proceed();
            } else {
                NotifPerms.showDeniedDialog(this, this::proceed);
            }
        }
    }

    private void proceed() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
