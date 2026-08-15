package de.gigaworks.seatbidding.round;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoundScheduleTest {
    private final RoundSchedule schedule = new RoundSchedule();
    private final ZoneId berlin = ZoneId.of("Europe/Berlin");

    @Test void calculatesFridayCutoffAndFollowingWeek() {
        var cutoff = schedule.nextCutoff("0 0 22 ? * FRI", berlin, Instant.parse("2026-08-04T10:00:00Z"));
        assertEquals(Instant.parse("2026-08-07T20:00:00Z"), cutoff);
        assertEquals(List.of(LocalDate.parse("2026-08-10"), LocalDate.parse("2026-08-11"),
                LocalDate.parse("2026-08-12"), LocalDate.parse("2026-08-13"), LocalDate.parse("2026-08-14")),
                schedule.targetDates(cutoff, berlin));
    }

    @Test void honorsDaylightSavingOffset() {
        assertEquals(Instant.parse("2026-10-23T20:00:00Z"),
                schedule.nextCutoff("0 0 22 ? * FRI", berlin, Instant.parse("2026-10-19T00:00:00Z")));
        assertEquals(Instant.parse("2026-10-30T21:00:00Z"),
                schedule.nextCutoff("0 0 22 ? * FRI", berlin, Instant.parse("2026-10-24T00:00:00Z")));
    }

    @Test void weekdayReminderUsesTheSameZoneAcrossDaylightSavingTime() {
        assertEquals(Instant.parse("2026-10-23T08:00:00Z"),
                schedule.nextCutoff("0 0 10 ? * MON-FRI", berlin, Instant.parse("2026-10-22T12:00:00Z")));
        assertEquals(Instant.parse("2026-10-26T09:00:00Z"),
                schedule.nextCutoff("0 0 10 ? * MON-FRI", berlin, Instant.parse("2026-10-23T12:00:00Z")));
    }
}