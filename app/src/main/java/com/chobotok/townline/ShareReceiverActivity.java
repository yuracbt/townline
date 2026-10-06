package com.chobotok.townline;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Share target: from any app use Share -> TownLine and the link
 * lands in your line under the Saved category, with a notification.
 */
public class ShareReceiverActivity extends Activity {

    private static final Pattern URL = Pattern.compile("https?://[^\\s]+");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        if (text == null) text = "";
        if (subject == null) subject = "";

        String link = firstUrl(text);
        if (link.isEmpty()) link = firstUrl(subject);

        String title = !subject.trim().isEmpty() ? subject.trim() : firstLine(text).trim();
        if (title.isEmpty() || URL.matcher(title).matches()) title = "Saved link";
        if (title.length() > 140) title = title.substring(0, 140);

        String desc = text.length() > 500 ? text.substring(0, 500) : text;

        if (link.isEmpty()) {
            Toast.makeText(this, "Nothing to save — no link in the shared content", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        NewsDbHelper db = new NewsDbHelper(this);
        boolean added = db.insertItemIfNew(-1, link, title, link, desc,
                System.currentTimeMillis(), "Saved");
        db.close();

        if (added) {
            Prefs prefs = new Prefs(this);
            if (prefs.isNotifyEnabled()) {
                SyncJobService.notifyShared(this, title);
            }
            Toast.makeText(this, "Saved to TownLine", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Already in your line", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private static String firstUrl(String s) {
        Matcher m = URL.matcher(s);
        return m.find() ? m.group() : "";
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }
}
