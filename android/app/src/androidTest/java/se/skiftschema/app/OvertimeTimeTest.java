package se.skiftschema.app;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OvertimeTimeTest {
    @Test public void springGapKeepsTheSelectedClockTimeUntilCorrected() throws Exception {
        LocalDate day = LocalDate.of(2027, 3, 27);
        Schedule schedule = schedule();
        Schedule.Span night = schedule.span(new Profile(), nightShift(schedule, day));
        OvertimeTime gap = OvertimeTime.from(night.start()).withClock(LocalTime.of(2, 30), night, true);
        assertEquals(day.plusDays(1).atTime(2, 30), gap.wall());
        assertTrue(gap.isGap());
        assertFalse(gap.isRepeated());

        OvertimeTime corrected = gap.withClock(LocalTime.of(3, 30), night, true);
        assertFalse(corrected.isGap());
        assertEquals(Instant.parse("2027-03-28T01:30:00Z").toEpochMilli(), corrected.instant());
    }

    @Test public void autumnOccurrencesHaveDistinctInstantsAndSurviveSaving() throws Exception {
        LocalDate day = LocalDate.of(2027, 10, 30);
        Schedule schedule = schedule();
        Schedule.Shift shift = nightShift(schedule, day);
        Profile profile = new Profile();
        Schedule.Span night = schedule.span(profile, shift);
        LocalTime repeatedClock = LocalTime.of(2, 30);
        OvertimeTime first = OvertimeTime.from(night.start()).withClock(repeatedClock, night, true);
        OvertimeTime second = first.otherOccurrence();
        assertTrue(first.isRepeated());
        assertEquals("1:a gången · UTC+02:00", first.occurrenceLabel());
        assertEquals("2:a gången · UTC+01:00", second.occurrenceLabel());
        assertEquals(day.plusDays(1).atTime(repeatedClock), second.wall());
        assertEquals(Instant.parse("2027-10-31T00:30:00Z").toEpochMilli(), first.instant());
        assertEquals(Instant.parse("2027-10-31T01:30:00Z").toEpochMilli(), second.instant());
        assertEquals(1, Instant.ofEpochMilli(first.instant()).until(Instant.ofEpochMilli(second.instant()), ChronoUnit.HOURS));

        profile.overtime.put(shift.key(), new Schedule.Work(shift, new Schedule.Span(first.instant(), second.instant()), true));
        Schedule.Span saved = Profile.parse(profile.json()).overtime.get(shift.key()).span();
        assertEquals(first, OvertimeTime.from(saved.start()));
        assertEquals(second, OvertimeTime.from(saved.end()));
        assertEquals(60 * 60 * 1000, saved.end() - saved.start());
        assertEquals(1, second.withClock(LocalTime.of(2, 45), night, false).occurrence());
    }

    private Schedule schedule() throws Exception {
        return new Schedule(InstrumentationRegistry.getInstrumentation().getTargetContext().getAssets());
    }

    private Schedule.Shift nightShift(Schedule schedule, LocalDate day) {
        return schedule.assignment("six", day).shifts().stream().filter(shift -> shift.slot() == 2)
                .findFirst().orElseThrow(AssertionError::new);
    }
}
