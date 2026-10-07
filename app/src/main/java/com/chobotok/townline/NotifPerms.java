package com.chobotok.townline;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Notification permission: check it, request it, and — when the user says no —
 * walk them straight into Android's app settings so they can allow it there.
 */
public final class NotifPerms {
    private NotifPerms() {}

    /** True when the app can actually post a notification right now. */
    public static boolean canNotify(Activity a) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        NotificationManager nm =
                (NotificationManager) a.getSystemService(Context.NOTIFICATION_SERVICE);
        // areNotificationsEnabled() is API 24+; minSdk is 26.
        return nm == null || nm.areNotificationsEnabled();
    }

    /**
     * Ask for the runtime permission on Android 13+; on older versions (or when
     * the system won't show the prompt again) jump straight to app settings.
     */
    public static void request(Activity a, int requestCode) {
        if (Build.VERSION.SDK_INT >= 33) {
            a.requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, requestCode);
        } else {
            openSettings(a);
        }
    }

    /** Opens this app's page in Android Settings, on the notifications screen. */
    public static void openSettings(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName()));
        } catch (Exception e) {
            try {
                a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + a.getPackageName())));
            } catch (Exception ignored) { }
        }
    }

    /**
     * Shown after the user denies the permission: explains why it matters and
     * offers a one-tap jump into Android settings. Runs {@code onDismiss} when
     * the user picks "Not now" or backs out.
     */
    public static void showDeniedDialog(Activity a, Runnable onDismiss) {
        new AlertDialog.Builder(a)
                .setTitle("Notifications are off")
                .setMessage("TownLine can't tell you about new stories until notifications " +
                        "are allowed. Open the app settings and switch notifications on.")
                .setPositiveButton("Open settings", (d, w) -> openSettings(a))
                .setNegativeButton("Not now",
                        (d, w) -> { if (onDismiss != null) onDismiss.run(); })
                .setOnCancelListener(d -> { if (onDismiss != null) onDismiss.run(); })
                .show();
    }
}
