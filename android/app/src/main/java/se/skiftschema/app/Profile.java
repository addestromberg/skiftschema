package se.skiftschema.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

final class Profile {
    String unit;
    String appearance = "system";
    String accent = "forest";
    final Map<String, String> crews = new LinkedHashMap<>();
    final Map<String, String> vacation = new LinkedHashMap<>();
    final Map<String, Schedule.Times> overrides = new LinkedHashMap<>();
    final Map<String, Schedule.Work> overtime = new LinkedHashMap<>();
    final Export export = new Export();

    static final class Export {
        boolean enabled;
        boolean sync;
        boolean pending;
        boolean rejected;
        Long calendarId;
        Integer reminder;
        final boolean[] categories = {true, true, false};
    }

    boolean owns(Schedule.Shift shift) {
        return shift.crew().equals(crews.get(shift.unit()));
    }

    static String string(JSONObject object, String key) {
        return object.isNull(key) ? null : object.optString(key, null);
    }

    static void put(JSONObject object, String key, Object value) {
        try {
            object.put(key, value == null ? JSONObject.NULL : value);
        } catch (JSONException e) {
            throw new IllegalArgumentException(key, e);
        }
    }

    static Profile parse(String input) throws JSONException {
        Profile profile = new Profile();
        JSONObject root = new JSONObject(input);
        JSONObject prefs = root.optJSONObject("prefs");
        if (prefs != null) {
            profile.unit = string(prefs, "unit");
            profile.appearance = prefs.optString("appearance", "system");
            profile.accent = prefs.optString("accent", "forest");
            readStrings(prefs.optJSONObject("crews"), profile.crews);
            readStrings(prefs.optJSONObject("vacation"), profile.vacation);
            JSONObject times = prefs.optJSONObject("overrides");
            if (times != null) {
                for (var keys = times.keys(); keys.hasNext(); ) {
                    String key = keys.next();
                    profile.overrides.put(key, Schedule.Times.read(times.getJSONObject(key)));
                }
            }
            JSONObject out = prefs.optJSONObject("export");
            if (out != null) {
                Export export = profile.export;
                export.enabled = out.optBoolean("enabled");
                export.sync = out.optBoolean("sync");
                export.pending = out.optBoolean("pending");
                export.rejected = out.optBoolean("rejected");
                if (!out.isNull("calendarId")) {
                    long id = out.optLong("calendarId", -1);
                    if (id >= 0) export.calendarId = id;
                }
                if (!out.isNull("reminder")) export.reminder = out.getInt("reminder");
                JSONObject categories = out.optJSONObject("categories");
                if (categories != null) {
                    for (int i = 0; i < Schedule.CATEGORIES.length; i++) {
                        export.categories[i] = categories.optBoolean(Schedule.CATEGORIES[i], export.categories[i]);
                    }
                }
            }
        }
        JSONObject entries = root.optJSONObject("overtime");
        if (entries != null) {
            for (var keys = entries.keys(); keys.hasNext(); ) {
                JSONObject entry = entries.getJSONObject(keys.next());
                String slot = entry.getString("slot");
                for (int i = 0; i < Schedule.SLOTS.length; i++) {
                    if (!Schedule.SLOTS[i].equals(slot)) continue;
                    Schedule.Shift shift = new Schedule.Shift(entry.getString("unit"),
                            LocalDate.parse(entry.getString("date")), i, entry.getString("crew"));
                    profile.overtime.put(shift.key(), new Schedule.Work(shift,
                            new Schedule.Span(entry.getLong("start"), entry.getLong("end")), true));
                }
            }
        }
        return profile;
    }

    private static void readStrings(JSONObject values, Map<String, String> target) {
        if (values == null) return;
        for (var keys = values.keys(); keys.hasNext(); ) {
            String key = keys.next();
            target.put(key, string(values, key));
        }
    }

    String json() {
        JSONObject root = new JSONObject();
        JSONObject prefs = new JSONObject();
        put(root, "prefs", prefs);
        put(prefs, "unit", unit);
        put(prefs, "appearance", appearance);
        put(prefs, "accent", accent);
        put(prefs, "crews", new JSONObject(crews));
        JSONObject groups = new JSONObject();
        vacation.forEach((key, value) -> put(groups, key, value));
        put(prefs, "vacation", groups);
        JSONObject times = new JSONObject();
        overrides.forEach((key, value) -> put(times, key, value.json()));
        put(prefs, "overrides", times);
        JSONObject out = new JSONObject();
        put(prefs, "export", out);
        put(out, "enabled", export.enabled);
        put(out, "sync", export.sync);
        put(out, "pending", export.pending);
        put(out, "rejected", export.rejected);
        put(out, "calendarId", export.calendarId == null ? null : export.calendarId.toString());
        put(out, "reminder", export.reminder);
        JSONObject categories = new JSONObject();
        for (int i = 0; i < Schedule.CATEGORIES.length; i++) {
            put(categories, Schedule.CATEGORIES[i], export.categories[i]);
        }
        put(out, "categories", categories);
        JSONObject entries = new JSONObject();
        put(root, "overtime", entries);
        overtime.forEach((key, work) -> {
            Schedule.Shift shift = work.shift();
            JSONObject entry = new JSONObject();
            put(entry, "unit", shift.unit());
            put(entry, "date", shift.date().toString());
            put(entry, "slot", Schedule.SLOTS[shift.slot()]);
            put(entry, "crew", shift.crew());
            put(entry, "start", work.span().start());
            put(entry, "end", work.span().end());
            put(entries, key, entry);
        });
        return root.toString();
    }

    static final class Store {
        private final File file;
        private SQLiteDatabase database;

        Store(Context context) {
            // Expo SQLite stored the profile here on Android.
            file = new File(new File(context.getFilesDir(), "SQLite"), "skiftschema.db");
        }

        private SQLiteDatabase open() {
            if (database == null) {
                File directory = file.getParentFile();
                if (!directory.isDirectory() && !directory.mkdirs()) {
                    throw new IllegalStateException("Could not create database directory");
                }
                database = SQLiteDatabase.openOrCreateDatabase(file, null);
                database.enableWriteAheadLogging();
                database.execSQL("CREATE TABLE IF NOT EXISTS state (id TEXT PRIMARY KEY, value TEXT NOT NULL)");
            }
            return database;
        }

        Profile load() throws JSONException {
            try (Cursor cursor = open().rawQuery("SELECT value FROM state WHERE id = ?",
                    new String[]{"profile"})) {
                return cursor.moveToFirst() ? parse(cursor.getString(0)) : new Profile();
            }
        }

        void save(String json) {
            ContentValues values = new ContentValues();
            values.put("id", "profile");
            values.put("value", json);
            open().replaceOrThrow("state", null, values);
        }

        void close() {
            if (database != null) database.close();
        }
    }
}
