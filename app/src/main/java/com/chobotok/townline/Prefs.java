package com.chobotok.townline;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static final String NAME = "townline";
    private final SharedPreferences p;

    public Prefs(Context c) {
        p = c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public boolean isFirstRun() { return p.getBoolean("first_run", true); }
    public void setFirstRunDone() { p.edit().putBoolean("first_run", false).apply(); }

    public boolean isSeeded() { return p.getBoolean("seeded", false); }
    public void setSeeded() { p.edit().putBoolean("seeded", true).apply(); }

    public String getTown() { return p.getString("town", "Airdrie, AB"); }
    public void setTown(String t) { p.edit().putString("town", t == null ? "" : t.trim()).apply(); }

    public int getIntervalHours() { return p.getInt("interval_hours", 4); }
    public void setIntervalHours(int h) { p.edit().putInt("interval_hours", h).apply(); }

    public boolean isNotifyEnabled() { return p.getBoolean("notify", true); }
    public void setNotifyEnabled(boolean b) { p.edit().putBoolean("notify", b).apply(); }

    public long getLastSync() { return p.getLong("last_sync", 0); }
    public void setLastSync(long t) { p.edit().putLong("last_sync", t).apply(); }
}
