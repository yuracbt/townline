package com.chobotok.townline;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** Periodic background scan: fetches every enabled source, stores new items, notifies. */
public class SyncJobService extends JobService {

    public static final int JOB_ID = 1001;
    private static final int ONE_OFF_ID = 1002;
    private static final int CHAIN_ID = 1003; // sub-15min intervals: one-off jobs chained together
    private static final String CHANNEL_ID = "townline_new";
    private static final int NOTIF_ID = 1;
    private static final String UA = "TownLine/1.0 (Android)";

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            try {
                doSync();
            } catch (Exception e) {
                android.util.Log.e("TownLine", "sync failed", e);
            }
            // sub-15min mode: chain the next run (setPeriodic can't go below 15 min)
            if (params.getJobId() == CHAIN_ID) {
                long intervalMs = Math.max(1, new Prefs(this).getIntervalMinutes()) * 60_000L;
                if (intervalMs < JobInfo.getMinPeriodMillis()) {
                    JobScheduler js =
                            (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
                    if (js != null) scheduleChain(js, this, intervalMs);
                }
            }
            jobFinished(params, false);
        }).start();
        return true; // work continues on background thread
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true; // reschedule if interrupted
    }

    /** Schedules (or re-schedules) the periodic scan using the saved interval. */
    public static void schedule(Context ctx) {
        Prefs prefs = new Prefs(ctx);
        long intervalMs = Math.max(1, prefs.getIntervalMinutes()) * 60_000L;
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        js.cancel(JOB_ID);
        js.cancel(CHAIN_ID);
        if (intervalMs >= JobInfo.getMinPeriodMillis()) {
            JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, SyncJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(intervalMs)
                    .setPersisted(true)
                    .build();
            js.schedule(job);
        } else {
            scheduleChain(js, ctx, intervalMs);
        }
    }

    private static void scheduleChain(JobScheduler js, Context ctx, long intervalMs) {
        JobInfo job = new JobInfo.Builder(CHAIN_ID, new ComponentName(ctx, SyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(intervalMs)
                .setOverrideDeadline(intervalMs * 2)
                .setPersisted(true)
                .build();
        js.schedule(job);
    }

    /** Triggers an immediate one-off scan (manual "Sync now"). */
    public static void syncNow(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        JobInfo job = new JobInfo.Builder(ONE_OFF_ID, new ComponentName(ctx, SyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(2000)
                .build();
        js.schedule(job);
    }

    public static String townQueryUrl(String town) {
        try {
            String q = URLEncoder.encode(town == null || town.isEmpty() ? "Airdrie, AB" : town, "UTF-8");
            return "https://news.google.com/rss/search?q=" + q + "&hl=en-CA&gl=CA&ceid=CA%3Aen";
        } catch (UnsupportedEncodingException e) {
            return "https://news.google.com/rss/search?q=Airdrie&hl=en-CA&gl=CA&ceid=CA%3Aen";
        }
    }

    private void doSync() {
        NewsDbHelper db = new NewsDbHelper(this);
        Prefs prefs = new Prefs(this);
        List<Source> sources = db.getSources();
        int totalNew = 0;
        List<String> newTitles = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (Source s : sources) {
            if (!s.enabled) continue;
            String url = s.townQuery ? townQueryUrl(prefs.getTown()) : s.url;
            try {
                List<RssParser.Parsed> items = fetch(url);
                int added = 0;
                for (RssParser.Parsed p : items) {
                    String cat = Categorizer.categorize(s.name, p.title, p.description);
                    if (db.insertItemIfNew(s.id, p.guid, p.title, p.link, p.description, p.pubDate, cat)) {
                        added++;
                        totalNew++;
                        if (newTitles.size() < 5) newTitles.add(p.title);
                    }
                }
                s.lastSync = now;
                s.lastError = null;
                s.lastCount = items.size();
            } catch (Exception e) {
                s.lastSync = now;
                String msg = e.getMessage();
                s.lastError = msg == null || msg.isEmpty() ? e.getClass().getSimpleName()
                        : (msg.length() > 120 ? msg.substring(0, 120) : msg);
            }
            db.updateSource(s);
        }

        db.pruneOld(600);
        prefs.setLastSync(now);
        db.close();

        if (totalNew > 0 && prefs.isNotifyEnabled() && notificationsAllowed()) {
            postNotification(totalNew, newTitles);
        }
    }

    private List<RssParser.Parsed> fetch(String urlStr) throws Exception {
        HttpURLConnection c = null;
        InputStream in = null;
        try {
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml");
            c.setConnectTimeout(15000);
            c.setReadTimeout(25000);
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            in = c.getInputStream();
            return RssParser.parse(in);
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
            if (c != null) c.disconnect();
        }
    }

    private boolean notificationsAllowed() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void postNotification(int count, List<String> titles) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription(getString(R.string.channel_desc));
            nm.createNotificationChannel(ch);
        }

        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.InboxStyle style = new Notification.InboxStyle()
                .setBigContentTitle(count + " new local stories");
        for (String t : titles) style.addLine(t);

        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(count == 1 ? "1 new local story" : count + " new local stories")
                .setContentText(titles.isEmpty() ? "" : titles.get(0))
                .setStyle(style)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(NOTIF_ID, n);
    }

    /** Notification for a single story shared into the app (e.g. from Facebook). */
    public static void notifyShared(Context ctx, String title) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription(ctx.getString(R.string.channel_desc));
            nm.createNotificationChannel(ch);
        }
        Intent i = new Intent(ctx, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Saved to TownLine")
                .setContentText(title)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(NOTIF_ID + 1, n);
    }
}
