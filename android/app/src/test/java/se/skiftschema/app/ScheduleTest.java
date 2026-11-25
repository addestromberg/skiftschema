package se.skiftschema.app;

import org.junit.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScheduleTest {
    @Test public void stockholmNightDurationUsesElapsedTimeAcrossClockChanges() {
        assertEquals(Duration.ofHours(7), nightDuration(LocalDate.of(2027, 3, 27)));
        assertEquals(Duration.ofHours(9), nightDuration(LocalDate.of(2027, 10, 30)));
        assertEquals(Duration.ofHours(8), nightDuration(LocalDate.of(2027, 12, 31)));
    }

    @Test public void januaryGridStartsOnMondayAndIncludesEachDayOnce() {
        List<LocalDate> days = Schedule.cells(LocalDate.of(2027, 1, 19), false);
        assertEquals(LocalDate.of(2026, 12, 28), days.get(0));
        assertEquals(LocalDate.of(2027, 1, 31), days.get(days.size() - 1));
        assertEquals(35, days.size());
        for (int index = 0; index < days.size(); index++) {
            assertEquals(days.get(0).plusDays(index), days.get(index));
        }
    }

    @Test public void shiftsThatShareAnEndpointDoNotOverlap() {
        Schedule.Span morning = new Schedule.Span(100, 200);
        assertFalse(morning.overlaps(new Schedule.Span(200, 300)));
        assertTrue(morning.overlaps(new Schedule.Span(199, 300)));
    }

    @Test public void weekRangeShowsBothYearsAcrossNewYear() {
        LocalDate start = Schedule.weekStart(LocalDate.of(2027, 1, 1));
        assertEquals("Mån 28 december 2026 – Sön 3 januari 2027", Schedule.weekRangeLabel(start));
    }

    @Test public void weekRangeShowsOneYearWithinTheSameYear() {
        assertEquals("Mån 4 januari – Sön 10 januari 2027", Schedule.weekRangeLabel(LocalDate.of(2027, 1, 4)));
    }

    private Duration nightDuration(LocalDate evening) {
        long start = Schedule.atTime(evening, LocalTime.of(22, 0));
        long end = Schedule.atTime(evening.plusDays(1), LocalTime.of(6, 0));
        return Duration.ofMillis(end - start);
    }
}
