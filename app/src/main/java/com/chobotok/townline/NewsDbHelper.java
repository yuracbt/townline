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
    private static final int VERSION = 3;

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
                " description TEXT, pub_date INTEGER, fetched_at INTEGER, is_new INTEGER DEFAULT 1," +
                " category TEXT)");
        db.execSQL("CREATE INDEX idx_items_pub ON items(pub_date DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS links (_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " kind TEXT, name TEXT, url TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS links (_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    " kind TEXT, name TEXT, url TEXT)");
        }
        if (oldV < 3) {
            db.execSQL("ALTER TABLE items ADD COLUMN category TEXT");
        }
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
                                   String description, long pubDate, String category) {
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
        v.put("category", category == null ? "News" : category);
        long row = getWritableDatabase().insertWithOnConflict("items", null, v,
                SQLiteDatabase.CONFLICT_IGNORE);
        return row != -1;
    }

    /** Backwards-compatible insert (defaults category to News). */
    public boolean insertItemIfNew(long sourceId, String guid, String title, String link,
                                   String description, long pubDate) {
        return insertItemIfNew(sourceId, guid, title, link, description, pubDate, "News");
    }

    public List<NewsItem> getItems(int limit) {
        return getItems(limit, "All");
    }

    public List<NewsItem> getItems(int limit, String category) {
        List<NewsItem> out = new ArrayList<>();
        String where = (category == null || "All".equals(category)) ? ""
                : " WHERE i.category=?";
        String[] args = (category == null || "All".equals(category)) ? null
                : new String[]{category};
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT i._id, i.source_id, s.name, i.guid, i.title, i.link, i.description," +
                " i.pub_date, i.is_new, i.category FROM items i LEFT JOIN sources s ON s._id=i.source_id" +
                where + " ORDER BY i.pub_date DESC, i._id DESC LIMIT " + limit, args);
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
            n.category = c.getString(9);
            out.add(n);
        }
        c.close();
        return out;
    }

    public List<String> getCategories() {
        List<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT DISTINCT category FROM items WHERE category IS NOT NULL" +
                " ORDER BY CASE category" +
                " WHEN 'Facebook' THEN 0 WHEN 'Events' THEN 1 WHEN 'News' THEN 2" +
                " WHEN 'Community' THEN 3 WHEN 'Business' THEN 4 WHEN 'Calgary' THEN 5" +
                " ELSE 6 END, category", null);
        while (c.moveToNext()) out.add(c.getString(0));
        c.close();
        return out;
    }

    public List<NewsItem> getUncategorized(int limit) {
        List<NewsItem> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT i._id, s.name, i.title, i.description FROM items i" +
                " LEFT JOIN sources s ON s._id=i.source_id" +
                " WHERE i.category IS NULL LIMIT " + limit, null);
        while (c.moveToNext()) {
            NewsItem n = new NewsItem();
            n.id = c.getLong(0);
            n.sourceName = c.getString(1);
            n.title = c.getString(2);
            n.description = c.getString(3);
            out.add(n);
        }
        c.close();
        return out;
    }

    public void setCategory(long itemId, String category) {
        ContentValues v = new ContentValues();
        v.put("category", category);
        getWritableDatabase().update("items", v, "_id=?", new String[]{String.valueOf(itemId)});
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

    // ---------- quick links (Facebook groups, event pages) ----------

    public long addLink(String kind, String name, String url) {
        ContentValues v = new ContentValues();
        v.put("kind", kind);
        v.put("name", name);
        v.put("url", url);
        return getWritableDatabase().insert("links", null, v);
    }

    public List<Link> getLinks(String kind) {
        List<Link> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("links", null, "kind=?",
                new String[]{kind}, null, null, "_id ASC");
        while (c.moveToNext()) {
            Link l = new Link();
            l.id = c.getLong(c.getColumnIndexOrThrow("_id"));
            l.kind = c.getString(c.getColumnIndexOrThrow("kind"));
            l.name = c.getString(c.getColumnIndexOrThrow("name"));
            l.url = c.getString(c.getColumnIndexOrThrow("url"));
            out.add(l);
        }
        c.close();
        return out;
    }

    public int getLinksCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM links", null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    public void deleteLink(long id) {
        getWritableDatabase().delete("links", "_id=?", new String[]{String.valueOf(id)});
    }
}
