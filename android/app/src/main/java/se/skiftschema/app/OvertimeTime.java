package se.skiftschema.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

record OvertimeTime(LocalDateTime wall, int occurrence) {
    static OvertimeTime from(long instant) {
        var local = Schedule.local(instant);
        LocalDateTime wall = local.toLocalDateTime();
        return new OvertimeTime(wall, offsets(wall).indexOf(local.getOffset()));
    }

    private static List<ZoneOffset> offsets(LocalDateTime wall) {
        return Schedule.STOCKHOLM.getRules().getValidOffsets(wall);
    }

    boolean isGap() { return offsets(wall).isEmpty(); }

    boolean isRepeated() { return offsets(wall).size() == 2; }

    OvertimeTime otherOccurrence() { return new OvertimeTime(wall, 1 - occurrence); }

    String occurrenceLabel() {
        String ordinal = occurrence == 0 ? "1:a gången" : "2:a gången";
        return ordinal + " · UTC" + offsets(wall).get(occurrence);
    }

    long instant() { return wall.toInstant(offsets(wall).get(occurrence)).toEpochMilli(); }

    OvertimeTime withClock(LocalTime clock, Schedule.Span shift, boolean start) {
        LocalDateTime first = Schedule.local(shift.start()).toLocalDateTime();
        LocalDateTime last = Schedule.local(shift.end()).toLocalDateTime();
        OvertimeTime candidate = null;
        for (LocalDate date = first.toLocalDate(); !date.isAfter(last.toLocalDate()); date = date.plusDays(1)) {
            LocalDateTime selected = date.atTime(clock);
            List<ZoneOffset> available = offsets(selected);
            int index = isRepeated() && available.size() == 2 ? occurrence : 0;
            OvertimeTime option = new OvertimeTime(selected, index);
            if (available.isEmpty()) {
                if (selected.isBefore(first) || selected.isAfter(last)) continue;
            } else if (!insideShift(option.instant(), shift, start)) {
                if (!option.isRepeated()) continue;
                option = option.otherOccurrence();
                if (!insideShift(option.instant(), shift, start)) continue;
            }
            if (date.equals(wall.toLocalDate())) return option;
            if (candidate == null) candidate = option;
        }
        return candidate != null ? candidate : new OvertimeTime(wall.toLocalDate().atTime(clock), 0);
    }

    private static boolean insideShift(long instant, Schedule.Span shift, boolean start) {
        return start ? instant >= shift.start() && instant < shift.end()
                : instant > shift.start() && instant <= shift.end();
    }
}
