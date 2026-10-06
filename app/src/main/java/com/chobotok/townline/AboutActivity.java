package com.chobotok.townline;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** About: author, version, what the app does. */
public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        setTitle("About");

        String version = "1.8";
        try {
            version = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) { }

        ((TextView) findViewById(R.id.aboutVersion))
                .setText("TownLine " + version);
    }
}
