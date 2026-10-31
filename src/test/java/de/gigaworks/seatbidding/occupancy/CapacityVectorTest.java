package de.gigaworks.seatbidding.occupancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.gigaworks.seatbidding.persistence.AttendancePeriod;
import org.junit.jupiter.api.Test;

class CapacityVectorTest {

    @Test
    void fullDayConsumesBothPeriods() {
        var available = new CapacityVector(2, 1);

        assertTrue(available.supports(AttendancePeriod.FULL_DAY));
        assertEquals(new CapacityVector(1, 0), available.consume(AttendancePeriod.FULL_DAY));
    }

    @Test
    void complementaryHalfDaysShareOnePhysicalCapacityUnit() {
        var available = new CapacityVector(1, 1);

        var afterMorning = available.consume(AttendancePeriod.MORNING_ONLY);

        assertFalse(afterMorning.supports(AttendancePeriod.MORNING_ONLY));
        assertTrue(afterMorning.supports(AttendancePeriod.AFTERNOON_ONLY));
        assertEquals(new CapacityVector(0, 0), afterMorning.consume(AttendancePeriod.AFTERNOON_ONLY));
    }

    @Test
    void unavailablePeriodCannotBeConsumed() {
        assertThrows(IllegalArgumentException.class,
                () -> new CapacityVector(0, 1).consume(AttendancePeriod.FULL_DAY));
    }
}