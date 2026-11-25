package se.skiftschema.app;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class PhoneCalendar {
    private static final String NAME = "Skiftschema";
    private static final String OWNER = "se.skiftschema.app";
    private static final String MARKER = "Skiftschema/v1:";
    private final ContentResolver resolver;

    record Event(String key, String title, long start, long end, boolean allDay, Integer reminder) {}
    private record Existing(long id, Event event) {}
    enum Range { KEEP, FUTURE, ALL }

    PhoneCalendar(ContentResolver resolver) { this.resolver = resolver; }

    Long open(Long preferred, boolean create) throws Exception {
        String[] columns = {Calendars._ID, Calendars.NAME, Calendars.OWNER_ACCOUNT};
        Long local = null;
        try (Cursor rows = resolver.query(Calendars.CONTENT_URI, columns,
                Calendars.CALENDAR_DISPLAY_NAME + " = ?", new String[]{NAME}, null)) {
            if (rows == null) throw new IllegalStateException("Calendar provider unavailable");
            while (rows.moveToNext()) {
                long id = rows.getLong(0);
                if (Objects.equals(id, preferred)) return id;
                if (OWNER.equals(rows.getString(1)) && OWNER.equals(rows.getString(2))) local = id;
            }
        }
        if (local != null || !create) return local;
        Uri asSyncAdapter = Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(Calendars.ACCOUNT_NAME, OWNER)
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build();
        ContentProviderOperation operation = ContentProviderOperation.newInsert(asSyncAdapter)
                .withValue(Calendars.NAME, OWNER)
                .withValue(Calendars.ACCOUNT_NAME, OWNER)
                .withValue(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .withValue(Calendars.OWNER_ACCOUNT, OWNER)
                .withValue(Calendars.CALENDAR_DISPLAY_NAME, NAME)
                .withValue(Calendars.CALENDAR_COLOR, 0xFF08735B)
                .withValue(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
                .withValue(Calendars.CALENDAR_TIME_ZONE, Schedule.STOCKHOLM.getId())
                .withValue(Calendars.VISIBLE, 1)
                .withValue(Calendars.SYNC_EVENTS, 1)
                .withValue(Calendars.MAX_REMINDERS, 1)
                .withValue(Calendars.ALLOWED_REMINDERS, Integer.toString(Reminders.METHOD_ALERT))
                .build();
        ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        operations.add(operation);
        Uri created = resolver.applyBatch(CalendarContract.AUTHORITY, operations)[0].uri;
        if (created == null) throw new IllegalStateException("Could not create calendar");
        return ContentUris.parseId(created);
    }

    static Map<String, Event> desired(Profile profile, Schedule schedule, long now) {
        Map<String, Event> events = new LinkedHashMap<>();
        Integer reminder = profile.export.reminder;
        if (profile.export.categories[1]) {
            for (Schedule.Work work : profile.overtime.values()) {
                String key = "overtime:" + work.shift().key();
                if (work.span().end() > now) events.put(key, new Event(key,
                        "Övertid · " + Schedule.SLOTS[work.shift().slot()],
                        work.span().start(), work.span().end(), false, reminder));
            }
        }
        LocalDate first = Schedule.civilDate(now).minusDays(1);
        for (Schedule.Roster roster : schedule.years(profile.unit).values()) {
            if (profile.export.categories[0]) {
                for (LocalDate day : roster.days().tailMap(first).keySet()) {
                    for (Schedule.Work work : schedule.personal(profile, day)) {
                        String key = "regular:" + work.shift().key();
                        if (work.span().end() > now) events.put(key, new Event(key,
                                work.shift().title() + " · Skift " + work.shift().crew(),
                                work.span().start(), work.span().end(), false, reminder));
                    }
                }
            }
            if (profile.export.categories[2] && "five".equals(roster.unit())) {
                Schedule.Vacation vacation = roster.group(profile.vacation
                        .get(Schedule.yearKey(roster.unit(), roster.year())));
                if (vacation == null) continue;
                for (Schedule.Period period : vacation.periods()) {
                    long end = Schedule.utcDay(period.end().plusDays(1));
                    String key = "vacation:" + roster.unit() + ":" + period.start();
                    if (end > now) events.put(key, new Event(key, "Semester",
                            Schedule.utcDay(period.start()), end, true, null));
                }
            }
        }
        return events;
    }

    private static int category(String key) {
        for (int i = 0; i < Schedule.CATEGORIES.length; i++) {
            if (key.startsWith(Schedule.CATEGORIES[i] + ":")) return i;
        }
        return -1;
    }

    private List<Existing> list(long calendar, boolean readReminders) {
        List<Existing> existing = new ArrayList<>();
        String[] columns = {Events._ID, Events.DESCRIPTION, Events.TITLE,
                Events.DTSTART, Events.DTEND, Events.ALL_DAY, Events.HAS_ALARM};
        String selection = Events.CALENDAR_ID + " = ? AND " + Events.DELETED + " = 0 AND "
                + Events.DESCRIPTION + " LIKE ?";
        List<Long> withAlarm = new ArrayList<>();
        try (Cursor rows = resolver.query(Events.CONTENT_URI, columns,
                selection, new String[]{Long.toString(calendar), MARKER + "%"}, Events._ID)) {
            if (rows == null) throw new IllegalStateException("Calendar events unavailable");
            while (rows.moveToNext()) {
                String note = rows.getString(1);
                if (note == null || !note.startsWith(MARKER)) continue;
                String key = note.substring(MARKER.length());
                if (category(key) < 0) continue;
                long id = rows.getLong(0);
                existing.add(new Existing(id, new Event(key, rows.getString(2), rows.getLong(3),
                        rows.getLong(4), rows.getInt(5) != 0, null)));
                if (readReminders && rows.getInt(6) != 0) withAlarm.add(id);
            }
        }
        Map<Long, Integer> minutes = new HashMap<>();
        for (int i = 0; i < withAlarm.size(); i += 100) {
            List<String> ids = new ArrayList<>();
            for (long id : withAlarm.subList(i, Math.min(i + 100, withAlarm.size()))) {
                ids.add(Long.toString(id));
            }
            String where = Reminders.EVENT_ID + " IN (" + String.join(",", ids) + ")";
            try (Cursor rows = resolver.query(Reminders.CONTENT_URI,
                    new String[]{Reminders.EVENT_ID, Reminders.MINUTES}, where, null, Reminders._ID)) {
                if (rows == null) throw new IllegalStateException("Calendar reminders unavailable");
                while (rows.moveToNext()) minutes.putIfAbsent(rows.getLong(0), rows.getInt(1));
            }
        }
        for (int i = 0; i < existing.size() && !minutes.isEmpty(); i++) {
            Existing entry = existing.get(i);
            Integer reminder = minutes.get(entry.id);
            if (reminder == null) continue;
            Event event = entry.event;
            existing.set(i, new Existing(entry.id, new Event(event.key, event.title, event.start,
                    event.end, event.allDay, reminder)));
        }
        return existing;
    }

    void reconcile(long calendar, Profile profile, Schedule schedule, long now) throws Exception {
        Map<String, Event> wanted = desired(profile, schedule, now);
        ArrayList<ContentProviderOperation> batch = new ArrayList<>();
        for (Existing existing : list(calendar, true)) {
            Event event = existing.event;
            if (!profile.export.categories[category(event.key)] || event.end <= now) continue;
            Event target = wanted.remove(event.key);
            if (target == null) batch.add(deleteEvent(existing.id));
            else if (!target.equals(event)) write(batch, calendar, existing.id, target);
            if (batch.size() >= 60) apply(batch);
        }
        for (Event event : wanted.values()) {
            write(batch, calendar, null, event);
            if (batch.size() >= 60) apply(batch);
        }
        apply(batch);
    }

    void clear(long calendar, int category, Range range, long now) throws Exception {
        ArrayList<ContentProviderOperation> batch = new ArrayList<>();
        for (Existing existing : list(calendar, false)) {
            Event event = existing.event;
            if (category(event.key) == category && (range == Range.ALL || event.start > now)) {
                batch.add(deleteEvent(existing.id));
                if (batch.size() >= 60) apply(batch);
            }
        }
        apply(batch);
    }

    private void apply(ArrayList<ContentProviderOperation> batch) throws Exception {
        if (!batch.isEmpty()) {
            resolver.applyBatch(CalendarContract.AUTHORITY, batch);
            batch.clear();
        }
    }

    private ContentProviderOperation deleteEvent(long id) {
        return ContentProviderOperation.newDelete(ContentUris.withAppendedId(Events.CONTENT_URI, id)).build();
    }

    private void write(ArrayList<ContentProviderOperation> batch, long calendar, Long id, Event event) {
        ContentProviderOperation.Builder edit = id == null
                ? ContentProviderOperation.newInsert(Events.CONTENT_URI).withValue(Events.CALENDAR_ID, calendar)
                : ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Events.CONTENT_URI, id));
        int eventOperation = batch.size();
        String zone = event.allDay ? "UTC" : Schedule.STOCKHOLM.getId();
        batch.add(edit.withValue(Events.TITLE, event.title)
                .withValue(Events.DESCRIPTION, MARKER + event.key)
                .withValue(Events.DTSTART, event.start)
                .withValue(Events.DTEND, event.end)
                .withValue(Events.EVENT_TIMEZONE, zone)
                .withValue(Events.EVENT_END_TIMEZONE, zone)
                .withValue(Events.ALL_DAY, event.allDay ? 1 : 0)
                .withValue(Events.HAS_ALARM, event.reminder == null ? 0 : 1)
                .build());
        if (id != null) {
            batch.add(ContentProviderOperation.newDelete(Reminders.CONTENT_URI)
                    .withSelection(Reminders.EVENT_ID + " = ?", new String[]{id.toString()}).build());
        }
        if (event.reminder != null) {
            ContentProviderOperation.Builder alarm = ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                    .withValue(Reminders.MINUTES, event.reminder)
                    .withValue(Reminders.METHOD, Reminders.METHOD_ALERT);
            if (id == null) alarm.withValueBackReference(Reminders.EVENT_ID, eventOperation);
            else alarm.withValue(Reminders.EVENT_ID, id);
            batch.add(alarm.build());
        }
    }
}
