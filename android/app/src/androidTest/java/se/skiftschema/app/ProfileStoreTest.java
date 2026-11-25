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

    private void putProfile(String json) {
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(database.getPath(), null, SQLiteDatabase.OPEN_READWRITE)) {
            db.execSQL("INSERT OR REPLACE INTO state (id, value) VALUES ('profile', ?)", new Object[]{json});
        }
    }
}
