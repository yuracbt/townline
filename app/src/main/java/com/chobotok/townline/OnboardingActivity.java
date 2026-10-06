package com.chobotok.townline;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/** First-run: town, notification permission, what needs a login (nothing). */
public class OnboardingActivity extends Activity {

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

            if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
            prefs.setFirstRunDone();
            SyncJobService.schedule(this);
            SyncJobService.syncNow(this);
            Toast.makeText(this, "Scanning your town now…", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}
