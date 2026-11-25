package se.skiftschema.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.DatabaseUtils;
import android.provider.CalendarContract.Events;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SdkSuppress(minSdkVersion = 29)
public class VacationExportTest {
    @Test public void yellowVacationExportsUtcMidnightsAndExpiresAtStockholmMidnight() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Schedule schedule = new Schedule(context.getAssets());
        Profile profile = new Profile();
        profile.unit = "five";
        profile.export.categories[0] = false;
        profile.export.categories[2] = true;
        profile.vacation.put("five:2027", "yellow");
        LocalDate start = LocalDate.of(2027, 7, 19);
        LocalDate exclusiveEnd = LocalDate.of(2027, 8, 9);
        String key = "vacation:five:" + start;
        long started = Schedule.atTime(start, LocalTime.of(0, 30));
        long ended = Schedule.atTime(exclusiveEnd, LocalTime.MIDNIGHT);

        PhoneCalendar.Event vacation = PhoneCalendar.desired(profile, schedule, started).get(key);
        assertTrue(vacation.allDay());
        assertEquals(Schedule.utcDay(start), vacation.start());
        assertEquals(Schedule.utcDay(exclusiveEnd), vacation.end());
        assertTrue(PhoneCalendar.desired(profile, schedule, ended - 1).containsKey(key));
        assertFalse(PhoneCalendar.desired(profile, schedule, ended).containsKey(key));

        try (CalendarProviderFixture provider = new CalendarProviderFixture(context)) {
            PhoneCalendar calendar = new PhoneCalendar(ContentResolver.wrap(provider));
            long id = calendar.open(null, true);
            calendar.reconcile(id, profile, schedule, started);
            calendar.clear(id, 2, PhoneCalendar.Range.FUTURE, started);
            assertEquals(1, DatabaseUtils.queryNumEntries(provider.database, "events"));
            calendar.reconcile(id, profile, schedule, ended);
            assertEquals(1, DatabaseUtils.queryNumEntries(provider.database, "events"));
        }
    }

    @Test public void winterCleanupKeepsTodayAndRemovesTomorrowWhileTimedEventsUseInstants() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LocalDate today = LocalDate.of(2027, 1, 10);
        LocalDate tomorrow = today.plusDays(1);
        long now = Schedule.atTime(today, LocalTime.of(0, 30));
        try (CalendarProviderFixture provider = new CalendarProviderFixture(context)) {
            PhoneCalendar calendar = new PhoneCalendar(ContentResolver.wrap(provider));
            long id = calendar.open(null, true);
            addEvent(provider, id, "vacation:today", Schedule.utcDay(today), Schedule.utcDay(tomorrow), true);
            addEvent(provider, id, "vacation:tomorrow", Schedule.utcDay(tomorrow), Schedule.utcDay(tomorrow.plusDays(1)), true);
            addEvent(provider, id, "regular:in-progress", now - 1, now + 600_000, false);
            addEvent(provider, id, "regular:upcoming", now + 1, now + 600_000, false);

            calendar.clear(id, 2, PhoneCalendar.Range.FUTURE, now);
            calendar.clear(id, 0, PhoneCalendar.Range.FUTURE, now);
            assertCount(provider, "vacation:today", 1);
            assertCount(provider, "vacation:tomorrow", 0);
            assertCount(provider, "regular:in-progress", 1);
            assertCount(provider, "regular:upcoming", 0);

            Profile profile = new Profile();
            profile.export.categories[2] = true;
            Schedule schedule = new Schedule(context.getAssets());
            long nextMidnight = Schedule.atTime(tomorrow, LocalTime.MIDNIGHT);
            calendar.reconcile(id, profile, schedule, nextMidnight);
            assertCount(provider, "vacation:today", 1);
            calendar.clear(id, 2, PhoneCalendar.Range.ALL, nextMidnight);
            assertCount(provider, "vacation:today", 0);
        }
    }

    private void addEvent(CalendarProviderFixture provider, long calendar, String key,
                          long start, long end, boolean allDay) {
        ContentValues values = new ContentValues();
        values.put(Events.CALENDAR_ID, calendar);
        values.put(Events.DESCRIPTION, "Skiftschema/v1:" + key);
        values.put(Events.DTSTART, start);
        values.put(Events.DTEND, end);
        values.put(Events.ALL_DAY, allDay ? 1 : 0);
        provider.database.insertOrThrow("events", null, values);
    }

    private void assertCount(CalendarProviderFixture provider, String key, int expected) {
        assertEquals(expected, DatabaseUtils.queryNumEntries(provider.database, "events",
                Events.DESCRIPTION + " = ?", new String[]{"Skiftschema/v1:" + key}));
    }
}
