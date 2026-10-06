package com.chobotok.townline;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class NewsDbHelper extends SQLiteOpenHelper {
    private static final String DB = "townline.db";
    private static final int VERSION = 1;

    public NewsDbHelper(Context c) {
        super(c, DB, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sources (_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " name TEXT, url TEXT, enabled INTEGER DEFAULT 1, town_query INTEGER DEFAULT 0," +
                " last_sync INTEGER DEFAULT 0, last_error TEXT, last_count INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE items (_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " source_id INTEGER, guid TEXT UNIQUE, title TEXT, link TEXT," +
                " description TEXT, pub_date INTEGER, fetched_at INTEGER, is_new INTEGER DEFAULT 1)");
        db.execSQL("CREATE INDEX idx_items_pub ON items(pub_date DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // v1: nothing to migrate
    }

    // ---------- sources ----------

    public long addSource(String name, String url, boolean enabled, boolean townQuery) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("url", url);
        v.put("enabled", enabled ? 1 : 0);
        v.put("town_query", townQuery ? 1 : 0);
        return getWritableDatabase().insert("sources", null, v);
    }

    public List<Source> getSources() {
        List<Source> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("sources", null, null, null, null, null, "_id ASC");
        while (c.moveToNext()) {
            Source s = new Source();
            s.id = c.getLong(c.getColumnIndexOrThrow("_id"));
            s.name = c.getString(c.getColumnIndexOrThrow("name"));
            s.url = c.getString(c.getColumnIndexOrThrow("url"));
            s.enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) == 1;
            s.townQuery = c.getInt(c.getColumnIndexOrThrow("town_query")) == 1;
            s.lastSync = c.getLong(c.getColumnIndexOrThrow("last_sync"));
            s.lastError = c.getString(c.getColumnIndexOrThrow("last_error"));
            s.lastCount = c.getInt(c.getColumnIndexOrThrow("last_count"));
            out.add(s);
        }
        c.close();
        return out;
    }

    public void updateSource(Source s) {
        ContentValues v = new ContentValues();
        v.put("name", s.name);
        v.put("url", s.url);
        v.put("enabled", s.enabled ? 1 : 0);
        v.put("town_query", s.townQuery ? 1 : 0);
        v.put("last_sync", s.lastSync);
        v.put("last_error", s.lastError);
        v.put("last_count", s.lastCount);
        getWritableDatabase().update("sources", v, "_id=?", new String[]{String.valueOf(s.id)});
    }

    public void deleteSource(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("items", "source_id=?", new String[]{String.valueOf(id)});
        db.delete("sources", "_id=?", new String[]{String.valueOf(id)});
    }

    // ---------- items ----------

    /** @return true if the item was new and inserted */
    public boolean insertItemIfNew(long sourceId, String guid, String title, String link,
                                   String description, long pubDate) {
        if (guid == null || guid.isEmpty()) guid = link;
        if (guid == null || guid.isEmpty()) return false;
        ContentValues v = new ContentValues();
        v.put("source_id", sourceId);
        v.put("guid", guid);
        v.put("title", title == null ? "" : title);
        v.put("link", link == null ? "" : link);
        v.put("description", description == null ? "" : description);
        v.put("pub_date", pubDate);
        v.put("fetched_at", System.currentTimeMillis());
        v.put("is_new", 1);
        long row = getWritableDatabase().insertWithOnConflict("items", null, v,
                SQLiteDatabase.CONFLICT_IGNORE);
        return row != -1;
    }

    public List<NewsItem> getItems(int limit) {
        List<NewsItem> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT i._id, i.source_id, s.name, i.guid, i.title, i.link, i.description," +
                " i.pub_date, i.is_new FROM items i LEFT JOIN sources s ON s._id=i.source_id" +
                " ORDER BY i.pub_date DESC, i._id DESC LIMIT " + limit, null);
        while (c.moveToNext()) {
            NewsItem n = new NewsItem();
            n.id = c.getLong(0);
            n.sourceId = c.getLong(1);
            n.sourceName = c.getString(2);
            n.guid = c.getString(3);
            n.title = c.getString(4);
            n.link = c.getString(5);
            n.description = c.getString(6);
            n.pubDate = c.getLong(7);
            n.isNew = c.getInt(8) == 1;
            out.add(n);
        }
        c.close();
        return out;
    }

    public int getNewCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM items WHERE is_new=1", null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    public void markRead(long itemId) {
        ContentValues v = new ContentValues();
        v.put("is_new", 0);
        getWritableDatabase().update("items", v, "_id=?", new String[]{String.valueOf(itemId)});
    }

    public void markAllRead() {
        ContentValues v = new ContentValues();
        v.put("is_new", 0);
        getWritableDatabase().update("items", v, null, null);
    }

    public void pruneOld(int keep) {
        getWritableDatabase().execSQL(
                "DELETE FROM items WHERE _id NOT IN (SELECT _id FROM items ORDER BY pub_date DESC, _id DESC LIMIT " + keep + ")");
    }
}
