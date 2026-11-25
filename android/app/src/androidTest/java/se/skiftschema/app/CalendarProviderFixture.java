package se.skiftschema.app;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.CalendarContract;

final class CalendarProviderFixture extends ContentProvider implements AutoCloseable {
    final SQLiteDatabase database = SQLiteDatabase.create(null);
    volatile Runnable beforeEventsQuery = () -> {};

    CalendarProviderFixture(Context context) {
        ProviderInfo info = new ProviderInfo();
        info.authority = CalendarContract.AUTHORITY;
        info.exported = true;
        attachInfo(context, info);
    }

    @Override public boolean onCreate() {
        database.execSQL("""
                CREATE TABLE calendars (_id INTEGER PRIMARY KEY, name TEXT, account_name TEXT, account_type TEXT,
                ownerAccount TEXT, calendar_displayName TEXT, calendar_color INTEGER, calendar_access_level INTEGER,
                calendar_timezone TEXT, visible INTEGER, sync_events INTEGER, maxReminders INTEGER, allowedReminders TEXT)
                """);
        database.execSQL("""
                CREATE TABLE events (_id INTEGER PRIMARY KEY, calendar_id INTEGER, title TEXT, description TEXT,
                dtstart INTEGER, dtend INTEGER, eventTimezone TEXT, eventEndTimezone TEXT, allDay INTEGER,
                hasAlarm INTEGER, deleted INTEGER DEFAULT 0)
                """);
        database.execSQL("CREATE TABLE reminders (_id INTEGER PRIMARY KEY, event_id INTEGER, minutes INTEGER, method INTEGER)");
        return true;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        String table = table(uri);
        if ("events".equals(table)) beforeEventsQuery.run();
        return database.query(table, projection, selection(uri, selection), args, null, null, sortOrder);
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        return ContentUris.withAppendedId(uri, database.insertOrThrow(table(uri), null, values));
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return database.update(table(uri), values, selection(uri, selection), args);
    }

    @Override public int delete(Uri uri, String selection, String[] args) {
        return database.delete(table(uri), selection(uri, selection), args);
    }

    @Override public String getType(Uri uri) { return null; }

    @Override public void close() { database.close(); }

    private static String table(Uri uri) { return uri.getPathSegments().get(0); }

    private static String selection(Uri uri, String selection) {
        if (uri.getPathSegments().size() == 1) return selection;
        return DatabaseUtils.concatenateWhere(selection, "_id = " + ContentUris.parseId(uri));
    }
}
