package se.skiftschema.app;

import android.content.res.AssetManager;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BundledRosterTest {
    @Test public void nativeAssignmentsAndVacationMatchTheSharedRosters() throws Exception {
        AssetManager assets = InstrumentationRegistry.getInstrumentation().getTargetContext().getAssets();
        List<String> files = Arrays.asList(assets.list("rosters"));
        assertTrue(files.containsAll(Arrays.asList("five-2026.json", "five-2027.json", "six-2026.json", "six-2027.json")));
        Schedule schedule = new Schedule(assets);
        for (String file : files) {
            if (!file.matches("(five|six)-\\d{4}\\.json")) continue;
            try (var reader = new BufferedReader(new InputStreamReader(assets.open("rosters/" + file), StandardCharsets.UTF_8))) {
                JSONObject source = new JSONObject(reader.lines().collect(Collectors.joining("\n")));
                String unit = source.getString("id");
                int year = source.getInt("year");
                assertEquals(file, unit + "-" + year + ".json");
                Schedule.Roster roster = schedule.roster(unit, year);
                assertNotNull(file, roster);
                verifyDays(source.getJSONObject("days"), roster);
                var times = source.getJSONObject("times");
                for (int index = 0; index < Schedule.TRANSITIONS.length; index++) {
                    assertEquals(LocalTime.parse(times.getString(Schedule.TRANSITIONS[index])), roster.times().at(index));
                }
                var groups = source.getJSONArray("vacation");
                assertEquals(groups.length(), roster.vacations().size());
                for (int index = 0; index < groups.length(); index++) {
                    var group = groups.getJSONObject(index);
                    var vacation = roster.group(group.getString("id"));
                    assertNotNull(vacation);
                    assertEquals(group.getString("label"), vacation.label());
                    var periods = group.getJSONArray("periods");
                    assertEquals(periods.length(), vacation.periods().size());
                    for (int period = 0; period < periods.length(); period++) {
                        var range = periods.getJSONArray(period);
                        assertEquals(new Schedule.Period(LocalDate.parse(range.getString(0)),
                                LocalDate.parse(range.getString(1))), vacation.periods().get(period));
                    }
                }
            }
        }
    }

    private void verifyDays(JSONObject days, Schedule.Roster roster) throws Exception {
        assertEquals(days.length(), roster.days().size());
        for (var dates = days.keys(); dates.hasNext();) {
            String date = dates.next();
            LocalDate day = LocalDate.parse(date);
            JSONObject source = days.getJSONObject(date);
            Schedule.Day assignment = roster.days().get(day);
            String message = roster.unit() + " " + date;
            assertNotNull(message, assignment);
            assertEquals(message, optional(source, "closure"), assignment.closure());
            assertEquals(message, optional(source, "note"), assignment.note());
            var slots = source.getJSONObject("slots");
            int count = 0;
            for (int slot = 0; slot < Schedule.SLOTS.length; slot++) {
                String crew = optional(slots, Schedule.SLOTS[slot]);
                if (crew == null) continue;
                assertTrue(message, assignment.shifts().contains(new Schedule.Shift(roster.unit(), day, slot, crew)));
                count++;
            }
            assertEquals(message, count, assignment.shifts().size());
        }
    }

    private String optional(JSONObject object, String key) {
        return object.isNull(key) ? null : object.optString(key);
    }
}
