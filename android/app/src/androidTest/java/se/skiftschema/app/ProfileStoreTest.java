package se.skiftschema.app;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ProfileStoreTest {
    private File directory;
    private File database;
    private Profile.Store store;

    @Before public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        directory = new File(context.getCacheDir(), UUID.randomUUID().toString());
        database = new File(directory, "SQLite/skiftschema.db");
        assertTrue(database.getParentFile().mkdirs());
        store = new Profile.Store(new ContextWrapper(context) {
            @Override public File getFilesDir() { return directory; }
        });
    }

    @After public void tearDown() {
        store.close();
        SQLiteDatabase.deleteDatabase(database);
        database.getParentFile().delete();
        directory.delete();
    }

    @Test public void failedProfileLoadBlocksWritesUntilRetrySucceeds() throws Exception {
        store.load();
        store.close();
        String original = "{\"prefs\":{\"unit\":\"six\",\"overrides\":{\"six:2027\":{}}},\"overtime\":{}}";
        putProfile(original);

        assertThrows(JSONException.class, store::load);
        assertThrows(IllegalStateException.class, () -> store.save(new Profile().json()));
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(database.getPath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor cursor = db.rawQuery("SELECT value FROM state WHERE id = 'profile'", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(original, cursor.getString(0));
        }

        putProfile("{\"prefs\":{\"unit\":\"six\"},\"overtime\":{}}");
        Profile recovered = store.load();
        assertEquals("six", recovered.unit);
        recovered.accent = "nord";
        store.save(recovered.json());
        assertEquals("nord", store.load().accent);
    }

    @Test public void sqliteFailureLeavesCorruptFileAvailableForRecovery() throws Exception {
        byte[] corruptFile = "invalid database contents".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream output = new FileOutputStream(database)) {
            output.write(corruptFile);
        }
        assertThrows(SQLiteException.class, store::load);
        assertThrows(IllegalStateException.class, () -> store.save(new Profile().json()));
        byte[] preserved = new byte[corruptFile.length];
        try (FileInputStream input = new FileInputStream(database)) {
            new java.io.DataInputStream(input).readFully(preserved);
            assertEquals(-1, input.read());
        }
        assertArrayEquals(corruptFile, preserved);
    }

    @Test public void opensAndUpdatesAnExistingExpoSqliteProfile() throws Exception {
        try (SQLiteDatabase expo = SQLiteDatabase.openOrCreateDatabase(database, null)) {
            expo.enableWriteAheadLogging();
            expo.execSQL("CREATE TABLE state (id TEXT PRIMARY KEY, value TEXT NOT NULL)");
            expo.execSQL("INSERT INTO state (id, value) VALUES ('profile', ?)", new Object[]{"""
                    {"prefs":{"unit":"five","appearance":"dark","accent":"nord","crews":{"five":"B","six":"D"},
                    "vacation":{"five:2027":"yellow"},"overrides":{"five:2027":{"cutoff":"05:30",
                    "weekdayEm":"14:00","weekdayNa":"22:00","weekendNa":"18:00"}},
                    "export":{"enabled":true,"sync":true,"pending":true,"calendarId":"42","reminder":10,
                    "categories":{"regular":false,"overtime":true,"vacation":true}}},
                    "overtime":{"five:2027-07-13:EM":{"unit":"five","date":"2027-07-13","slot":"EM",
                    "crew":"D","start":1815480000000,"end":1815494400000}}}
                    """});

            Profile profile = store.load();
            assertEquals("five", profile.unit);
            assertEquals("D", profile.crews.get("six"));
            assertEquals("yellow", profile.vacation.get("five:2027"));
            assertEquals(LocalTime.of(5, 30), profile.overrides.get("five:2027").cutoff());
            assertEquals(Long.valueOf(42), profile.export.calendarId);
            assertEquals(Integer.valueOf(10), profile.export.reminder);
            assertTrue(profile.export.pending);
            Schedule.Work overtime = profile.overtime.get("five:2027-07-13:EM");
            assertEquals(new Schedule.Shift("five", LocalDate.of(2027, 7, 13), 1, "D"), overtime.shift());
            assertEquals(new Schedule.Span(1815480000000L, 1815494400000L), overtime.span());

            profile.appearance = "light";
            store.save(profile.json());
            try (Cursor row = expo.rawQuery("SELECT value FROM state WHERE id = 'profile'", null)) {
                assertTrue(row.moveToFirst());
                Profile saved = Profile.parse(row.getString(0));
                assertEquals("light", saved.appearance);
                assertEquals(overtime, saved.overtime.get(overtime.shift().key()));
            }
        }
    }

    private void putProfile(String json) {
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(database.getPath(), null, SQLiteDatabase.OPEN_READWRITE)) {
            db.execSQL("INSERT OR REPLACE INTO state (id, value) VALUES ('profile', ?)", new Object[]{json});
        }
    }
}
