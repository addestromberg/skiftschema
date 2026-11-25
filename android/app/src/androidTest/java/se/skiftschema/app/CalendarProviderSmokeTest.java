package se.skiftschema.app;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@SdkSuppress(minSdkVersion = 29)
public class CalendarProviderSmokeTest {
    @Test public void reconcilesAndCleansAnIsolatedCalendarUsingRuntimePermissions() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var context = instrumentation.getTargetContext();
        for (String permission : new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}) {
            instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(), permission);
            assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(permission));
        }

        ContentResolver resolver = context.getContentResolver();
        String account = "skiftschema-test-" + UUID.randomUUID();
        ContentValues values = new ContentValues();
        values.put(Calendars.NAME, account);
        values.put(Calendars.ACCOUNT_NAME, account);
        values.put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL);
        values.put(Calendars.OWNER_ACCOUNT, account);
        values.put(Calendars.CALENDAR_DISPLAY_NAME, "Skiftschema Android test");
        values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER);
        values.put(Calendars.CALENDAR_TIME_ZONE, Schedule.STOCKHOLM.getId());
        values.put(Calendars.VISIBLE, 1);
        values.put(Calendars.SYNC_EVENTS, 1);
        values.put(Calendars.MAX_REMINDERS, 1);
        values.put(Calendars.ALLOWED_REMINDERS, Integer.toString(Reminders.METHOD_ALERT));
        Uri calendar = resolver.insert(asSyncAdapter(Calendars.CONTENT_URI, account), values);
        assertNotNull(calendar);
        long id = ContentUris.parseId(calendar);
        try {
            Profile profile = new Profile();
            profile.unit = "five";
            profile.export.categories[0] = false;
            profile.export.reminder = 10;
            LocalDate date = LocalDate.of(2027, 7, 13);
            long start = Schedule.atTime(date, LocalTime.of(14, 0));
            long end = Schedule.atTime(date, LocalTime.of(18, 0));
            long now = start - 60_000;
            Schedule.Shift shift = new Schedule.Shift("five", date, 1, "D");
            profile.overtime.put(shift.key(), new Schedule.Work(shift, new Schedule.Span(start, end), true));
            PhoneCalendar phone = new PhoneCalendar(resolver);
            Schedule schedule = new Schedule(context.getAssets());

            phone.reconcile(id, profile, schedule, now);
            long event = assertEvent(resolver, id, start, end, 10);
            phone.reconcile(id, profile, schedule, now);
            assertEquals(event, assertEvent(resolver, id, start, end, 10));

            profile.export.reminder = 20;
            profile.overtime.put(shift.key(), new Schedule.Work(shift, new Schedule.Span(start, end + 60_000), true));
            phone.reconcile(id, profile, schedule, now);
            assertEquals(event, assertEvent(resolver, id, start, end + 60_000, 20));

            phone.clear(id, 1, PhoneCalendar.Range.FUTURE, start + 1);
            assertEquals(event, assertEvent(resolver, id, start, end + 60_000, 20));
            phone.clear(id, 1, PhoneCalendar.Range.FUTURE, now);
            try (var rows = resolver.query(Events.CONTENT_URI, new String[]{Events._ID},
                    Events.CALENDAR_ID + " = ? AND " + Events.DELETED + " = 0", new String[]{Long.toString(id)}, null)) {
                assertNotNull(rows);
                assertFalse(rows.moveToFirst());
            }
        } finally {
            resolver.delete(asSyncAdapter(ContentUris.withAppendedId(Calendars.CONTENT_URI, id), account), null, null);
        }
    }

    private long assertEvent(ContentResolver resolver, long calendarId, long start, long end, int alarm) {
        long event;
        try (var rows = resolver.query(Events.CONTENT_URI, new String[]{Events._ID, Events.DTSTART, Events.DTEND},
                Events.CALENDAR_ID + " = ? AND " + Events.DELETED + " = 0", new String[]{Long.toString(calendarId)}, null)) {
            assertNotNull(rows);
            assertTrue(rows.moveToFirst());
            event = rows.getLong(0);
            assertEquals(start, rows.getLong(1));
            assertEquals(end, rows.getLong(2));
            assertFalse(rows.moveToNext());
        }
        try (var reminders = resolver.query(Reminders.CONTENT_URI, new String[]{Reminders.MINUTES},
                Reminders.EVENT_ID + " = ?", new String[]{Long.toString(event)}, null)) {
            assertNotNull(reminders);
            assertTrue(reminders.moveToFirst());
            assertEquals(alarm, reminders.getInt(0));
            assertFalse(reminders.moveToNext());
        }
        return event;
    }

    private Uri asSyncAdapter(Uri uri, String account) {
        return uri.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(Calendars.ACCOUNT_NAME, account)
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build();
    }
}
