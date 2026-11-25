package se.skiftschema.app;

import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

final class Schedule {
    static final ZoneId STOCKHOLM = ZoneId.of("Europe/Stockholm");
    static final Locale SWEDISH = Locale.forLanguageTag("sv-SE");
    static final String[] MONTHS = {"Januari", "Februari", "Mars", "April", "Maj", "Juni",
            "Juli", "Augusti", "September", "Oktober", "November", "December"};
    static final String[] WEEKDAYS = {"Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön"};
    static final String[] SLOTS = {"FM", "EM", "NA"};
    static final String[] SLOT_NAMES = {"Förmiddag", "Eftermiddag", "Natt"};
    static final String[] CATEGORIES = {"regular", "overtime", "vacation"};
    static final String[] CATEGORY_NAMES = {"Ordinarie pass", "Övertid", "Semester"};
    static final String[] TRANSITIONS = {"cutoff", "weekdayEm", "weekdayNa", "weekendNa"};
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    record Period(LocalDate start, LocalDate end) {
        boolean contains(LocalDate day) {
            return !day.isBefore(start) && !day.isAfter(end);
        }
    }

    record Vacation(String id, String label, List<Period> periods) {
        String detail() {
            List<String> labels = new ArrayList<>();
            for (Period period : periods) {
                labels.add(shortDate(period.start) + "–" + shortDate(period.end));
            }
            return String.join(", ", labels);
        }
    }

    record Shift(String unit, LocalDate date, int slot, String crew) {
        String key() { return unit + ":" + date + ":" + SLOTS[slot]; }
        String title() { return SLOT_NAMES[slot]; }
    }

    record Span(long start, long end) {
        boolean overlaps(Span other) { return start < other.end && other.start < end; }

        String label() {
            return clock(start) + "–" + clock(end)
                    + (civilDate(start).equals(civilDate(end)) ? "" : " nästa dag");
        }
    }

    record Work(Shift shift, Span span, boolean overtime) {}
    record Day(List<Shift> shifts, String closure, String note) {}

    record Times(LocalTime cutoff, LocalTime weekdayEm, LocalTime weekdayNa, LocalTime weekendNa) {
        private static int fromCutoff(LocalTime time, LocalTime cutoff) {
            return Math.floorMod(time.toSecondOfDay() - cutoff.toSecondOfDay(), 86400);
        }

        boolean valid() {
            int afternoon = fromCutoff(weekdayEm, cutoff);
            return afternoon > 0 && fromCutoff(weekdayNa, cutoff) > afternoon
                    && fromCutoff(weekendNa, cutoff) > 0;
        }

        LocalTime at(int index) {
            return switch (index) {
                case 0 -> cutoff;
                case 1 -> weekdayEm;
                case 2 -> weekdayNa;
                default -> weekendNa;
            };
        }

        Times with(int index, LocalTime time) {
            return new Times(index == 0 ? time : cutoff, index == 1 ? time : weekdayEm,
                    index == 2 ? time : weekdayNa, index == 3 ? time : weekendNa);
        }

        static Times read(JSONObject data) throws JSONException {
            return new Times(LocalTime.parse(data.getString(TRANSITIONS[0])),
                    LocalTime.parse(data.getString(TRANSITIONS[1])),
                    LocalTime.parse(data.getString(TRANSITIONS[2])),
                    LocalTime.parse(data.getString(TRANSITIONS[3])));
        }

        JSONObject json() {
            JSONObject result = new JSONObject();
            for (int i = 0; i < TRANSITIONS.length; i++) {
                Profile.put(result, TRANSITIONS[i], CLOCK.format(at(i)));
            }
            return result;
        }
    }

    record Roster(String unit, int year, Times times, List<Vacation> vacations,
                  TreeMap<LocalDate, Day> days) {
        Vacation group(String id) {
            for (Vacation group : vacations) if (group.id.equals(id)) return group;
            return null;
        }
    }

    private final Map<String, TreeMap<Integer, Roster>> rosters = new LinkedHashMap<>();

    Schedule(AssetManager assets) throws IOException, JSONException {
        for (String file : assets.list("rosters")) {
            if (!file.matches("(five|six)-\\d{4}\\.json")) continue;
            try (InputStream input = assets.open("rosters/" + file);
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[8192];
                int read;
                while ((read = input.read(chunk)) != -1) buffer.write(chunk, 0, read);
                load(new JSONObject(buffer.toString("UTF-8")));
            }
        }
        if (rosters.isEmpty()) throw new IOException("No bundled rosters");
    }

    private void load(JSONObject json) throws JSONException {
        String unit = json.getString("id");
        int year = json.getInt("year");
        List<Vacation> vacations = new ArrayList<>();
        JSONArray groups = json.getJSONArray("vacation");
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i);
            JSONArray ranges = group.getJSONArray("periods");
            List<Period> periods = new ArrayList<>();
            for (int j = 0; j < ranges.length(); j++) {
                JSONArray range = ranges.getJSONArray(j);
                periods.add(new Period(LocalDate.parse(range.getString(0)),
                        LocalDate.parse(range.getString(1))));
            }
            vacations.add(new Vacation(group.getString("id"), group.getString("label"), periods));
        }

        JSONObject entries = json.getJSONObject("days");
        TreeMap<LocalDate, Day> days = new TreeMap<>();
        for (var dates = entries.keys(); dates.hasNext(); ) {
            String date = dates.next();
            LocalDate day = LocalDate.parse(date);
            JSONObject entry = entries.getJSONObject(date);
            JSONObject slots = entry.getJSONObject("slots");
            List<Shift> shifts = new ArrayList<>(3);
            for (int slot = 0; slot < SLOTS.length; slot++) {
                String crew = Profile.string(slots, SLOTS[slot]);
                if (crew != null) shifts.add(new Shift(unit, day, slot, crew));
            }
            days.put(day, new Day(shifts, Profile.string(entry, "closure"),
                    Profile.string(entry, "note")));
        }

        rosters.computeIfAbsent(unit, ignored -> new TreeMap<>())
                .put(year, new Roster(unit, year, Times.read(json.getJSONObject("times")), vacations, days));
    }

    Map<Integer, Roster> years(String unit) {
        TreeMap<Integer, Roster> years = rosters.get(unit);
        return years == null ? new TreeMap<>() : years;
    }

    Roster roster(String unit, int year) {
        return years(unit).get(year);
    }

    int latestYear(String unit, long now) {
        int year = civilDate(now).getYear();
        int result = year;
        for (int available : years(unit).keySet()) {
            if (available == year) return year;
            result = available;
        }
        return result;
    }

    Day assignment(String unit, LocalDate day) {
        Roster roster = roster(unit, day.getYear());
        return roster == null ? null : roster.days.get(day);
    }

    Times times(Profile profile, String unit, int year) {
        Times override = profile.overrides.get(yearKey(unit, year));
        if (override != null) return override;
        Roster roster = roster(unit, year);
        if (roster != null) return roster.times;
        boolean five = "five".equals(unit);
        return new Times(LocalTime.of(6, 0), LocalTime.of(five ? 14 : 13, 0),
                LocalTime.of(five ? 22 : 21, 0), LocalTime.of(18, 0));
    }

    LocalDate activeDay(Profile profile, long now) {
        LocalDate civil = civilDate(now);
        long cutoff = atTime(civil, times(profile, profile.unit, civil.getYear()).cutoff);
        return now < cutoff ? civil.minusDays(1) : civil;
    }

    Span span(Profile profile, Shift shift) {
        LocalDate day = shift.date;
        Times rule = times(profile, shift.unit, day.getYear());
        LocalTime night = weekend(day) ? rule.weekendNa : rule.weekdayNa;
        if (shift.slot == 2) {
            LocalDate next = day.plusDays(1);
            return new Span(shiftTime(day, night, rule.cutoff),
                    atTime(next, times(profile, shift.unit, next.getYear()).cutoff));
        }
        LocalTime start = shift.slot == 0 ? rule.cutoff : rule.weekdayEm;
        LocalTime end = shift.slot == 1 || weekend(day) ? night : rule.weekdayEm;
        return new Span(shiftTime(day, start, rule.cutoff), shiftTime(day, end, rule.cutoff));
    }

    Vacation vacation(Profile profile, String unit, LocalDate day) {
        if (!"five".equals(unit)) return null;
        Roster roster = roster(unit, day.getYear());
        if (roster == null) return null;
        Vacation group = roster.group(profile.vacation.get(yearKey(unit, day.getYear())));
        if (group != null) {
            for (Period period : group.periods) if (period.contains(day)) return group;
        }
        return null;
    }

    List<Work> personal(Profile profile, LocalDate day) {
        List<Work> work = new ArrayList<>(1);
        Day entry = assignment(profile.unit, day);
        if (entry == null || vacation(profile, profile.unit, day) != null) return work;
        for (Shift shift : entry.shifts) {
            if (profile.owns(shift)) work.add(new Work(shift, span(profile, shift), false));
        }
        return work;
    }

    Work next(Profile profile, long now) {
        Work next = profile.overtime.values().stream()
                .filter(work -> work.span.start > now)
                .min(Comparator.comparingLong(work -> work.span.start)).orElse(null);
        if (profile.unit == null) return next;
        LocalDate active = activeDay(profile, now);
        for (Roster roster : years(profile.unit).values()) {
            if (roster.year < active.getYear()) continue;
            for (LocalDate day : roster.days.tailMap(active).keySet()) {
                for (Work work : personal(profile, day)) {
                    if (work.span.start > now) {
                        return next == null || work.span.start < next.span.start ? work : next;
                    }
                }
            }
        }
        return next;
    }

    Work conflict(Profile profile, Work candidate) {
        for (Work work : profile.overtime.values()) {
            if (!work.shift.key().equals(candidate.shift.key())
                    && work.span.overlaps(candidate.span)) return work;
        }
        for (int offset = -1; offset <= 1; offset++) {
            for (Work work : personal(profile, candidate.shift.date.plusDays(offset))) {
                if (work.span.overlaps(candidate.span)) return work;
            }
        }
        return null;
    }

    static String yearKey(String unit, int year) { return unit + ":" + year; }
    static String label(String unit) { return "five".equals(unit) ? "5-skift" : "6-skift"; }
    static String teams(String unit) { return "five".equals(unit) ? "ABCDE" : "ABCDEF"; }
    static String weekday(LocalDate day) { return WEEKDAYS[day.getDayOfWeek().getValue() - 1]; }
    static String month(LocalDate day) { return MONTHS[day.getMonthValue() - 1]; }
    static String shortDate(LocalDate day) { return day.getDayOfMonth() + " " + month(day).toLowerCase(SWEDISH); }
    static String dateLabel(LocalDate day) { return weekday(day) + " " + shortDate(day); }
    static boolean weekend(LocalDate day) { return day.getDayOfWeek().getValue() >= 6; }

    static LocalDate weekStart(LocalDate day) {
        return day.minusDays(day.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    }

    static String weekRangeLabel(LocalDate start) {
        LocalDate end = start.plusDays(6);
        String first = dateLabel(start);
        if (start.getYear() != end.getYear()) first += " " + start.getYear();
        return first + " – " + dateLabel(end) + " " + end.getYear();
    }

    static List<LocalDate> cells(LocalDate anchor, boolean week) {
        LocalDate first = week ? weekStart(anchor) : YearMonth.from(anchor).atDay(1);
        LocalDate start = weekStart(first);
        int size = week ? 7 : (first.getDayOfWeek().getValue() - 1 + first.lengthOfMonth() + 6) / 7 * 7;
        List<LocalDate> cells = new ArrayList<>(size);
        for (int i = 0; i < size; i++) cells.add(start.plusDays(i));
        return cells;
    }

    static ZonedDateTime local(long time) { return Instant.ofEpochMilli(time).atZone(STOCKHOLM); }
    static LocalDate civilDate(long time) { return local(time).toLocalDate(); }
    static String clock(long time) { return clock(local(time).toLocalTime()); }
    static String clock(LocalTime time) { return CLOCK.format(time); }
    static long utcDay(LocalDate day) { return day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(); }
    static long atTime(LocalDate day, LocalTime time) {
        return day.atTime(time).atZone(STOCKHOLM).toInstant().toEpochMilli();
    }

    private static long shiftTime(LocalDate day, LocalTime clock, LocalTime cutoff) {
        return atTime(clock.isBefore(cutoff) ? day.plusDays(1) : day, clock);
    }
}
