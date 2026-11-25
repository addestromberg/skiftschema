package se.skiftschema.app;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SdkSuppress(minSdkVersion = 29)
public class CalendarSyncTest {
    @Test public void approvedSyncReconcilesAnOvertimeEditAfterTheWriteFinishes() throws Exception {
        verifyOvertimeDuringSync(false);
    }

    @Test public void newerPreferencesKeepTheirPendingApprovalAfterTheWriteFinishes() throws Exception {
        verifyOvertimeDuringSync(true);
    }

    private void verifyOvertimeDuringSync(boolean newerPreferences) throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        var automation = instrumentation.getUiAutomation();
        automation.grantRuntimePermission(context.getPackageName(), Manifest.permission.READ_CALENDAR);
        automation.grantRuntimePermission(context.getPackageName(), Manifest.permission.WRITE_CALENDAR);

        File directory = new File(context.getCacheDir(), UUID.randomUUID().toString());
        Profile.Store store = new Profile.Store(new ContextWrapper(context) {
            @Override public File getFilesDir() { return directory; }
        });
        Profile profile = store.load();
        profile.unit = "six";
        profile.crews.put("six", "A");
        profile.export.enabled = true;
        profile.export.sync = true;
        profile.export.pending = true;
        profile.export.categories[0] = false;

        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        ExecutorService[] disk = new ExecutorService[1];
        ExecutorService[] worker = new ExecutorService[1];
        try (CalendarProviderFixture calendar = new CalendarProviderFixture(context);
             ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            calendar.database.execSQL("""
                    INSERT INTO calendars (_id, name, account_name, account_type, ownerAccount, calendar_displayName)
                    VALUES (1, 'se.skiftschema.app', 'se.skiftschema.app', 'LOCAL', 'se.skiftschema.app', 'Skiftschema')
                    """);
            calendar.beforeEventsQuery = () -> {
                if (reads.incrementAndGet() != 1) return;
                reading.countDown();
                try {
                    assertTrue(finish.await(20, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            };
            scenario.onActivity(app -> {
                disk[0] = (ExecutorService) get(app, "disk");
                worker[0] = (ExecutorService) get(app, "provider");
            });
            drain(disk[0]);
            drain(worker[0]);

            try {
                scenario.onActivity(app -> {
                    Profile.Store previous = (Profile.Store) get(app, "store");
                    disk[0].execute(previous::close);
                    set(app, "store", store);
                    set(app, "phoneCalendar", new PhoneCalendar(ContentResolver.wrap(calendar)));
                    app.profile = profile;
                    app.sync(true);
                });
                assertTrue(reading.await(20, TimeUnit.SECONDS));
                scenario.onActivity(app -> {
                    if (newerPreferences) {
                        profile.export.reminder = 10;
                        app.preferencesChanged(true);
                        app.dismissSheet();
                    }
                    long start = app.now() + TimeUnit.HOURS.toMillis(1);
                    var shift = new Schedule.Shift("six", LocalDate.now(Schedule.STOCKHOLM), 0, "B");
                    profile.overtime.put(shift.key(), new Schedule.Work(shift,
                            new Schedule.Span(start, start + TimeUnit.HOURS.toMillis(1)), true));
                    app.overtimeChanged();
                    assertTrue(app.calendarBusy);
                });
                finish.countDown();
                drain(worker[0]);
                drain(worker[0]);
                scenario.onActivity(app -> {
                    assertEquals(newerPreferences, profile.export.pending);
                    assertFalse(app.calendarBusy);
                });
                assertEquals(newerPreferences ? 1 : 2, reads.get());
                assertEquals(newerPreferences ? 0 : 1,
                        DatabaseUtils.queryNumEntries(calendar.database, "events", "description LIKE ?",
                                new String[]{"Skiftschema/v1:overtime:%"}));
            } finally {
                finish.countDown();
            }
        } finally {
            if (disk[0] != null && disk[0].isShutdown()) disk[0].awaitTermination(10, TimeUnit.SECONDS);
            store.close();
            File database = new File(directory, "SQLite/skiftschema.db");
            SQLiteDatabase.deleteDatabase(database);
            database.getParentFile().delete();
            directory.delete();
        }
    }

    private static void drain(ExecutorService executor) throws Exception {
        executor.submit(() -> {}).get(20, TimeUnit.SECONDS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static Object get(MainActivity app, String name) {
        try {
            return field(name).get(app);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static void set(MainActivity app, String name, Object value) {
        try {
            field(name).set(app, value);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
