package de.gigaworks.seatbidding.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.gigaworks.seatbidding.persistence.AllocationUnitType;
import de.gigaworks.seatbidding.persistence.AttendancePeriod;
import de.gigaworks.seatbidding.persistence.PhysicalSeatEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SeatPlacementOptimizerTest {

    private final SeatPlacementOptimizer optimizer = new SeatPlacementOptimizer();

    @Test
    void globallyAvoidsGivingAnIndifferentEmployeeAnotherEmployeesPreferredSeat() {
        var first = result("employee:1", 20, 1, 1);
        var second = result("employee:2", 10, 2, 2);
        var seats = List.of(seat(101, 1), seat(102, 2));

        var placement = optimizer.place(List.of(first, second), seats,
                Map.of(2L, Map.of(101L, 3, 102L, 1)));

        assertEquals(102L, placement.byFairnessIdentity().get("employee:1").id);
        assertEquals(101L, placement.byFairnessIdentity().get("employee:2").id);
    }

    @Test
    void tokenOrderBreaksAnOtherwiseEqualFairnessChoice() {
        var first = result("employee:1", 20, 1, 1);
        var second = result("employee:2", 10, 2, 2);
        var seats = List.of(seat(101, 1), seat(102, 2));

        var placement = optimizer.place(List.of(first, second), seats,
                Map.of(1L, Map.of(101L, 3), 2L, Map.of(101L, 3)));

        assertEquals(101L, placement.byFairnessIdentity().get("employee:1").id);
        assertEquals(102L, placement.byFairnessIdentity().get("employee:2").id);
    }

    private static RoundAllocation.Result result(String identity, int tokens, int rank, long employeeId) {
        var member = new RoundAllocation.MemberResult(employeeId, employeeId, tokens,
                AttendancePeriod.FULL_DAY, (short) 1, rank);
        return new RoundAllocation.Result(1, LocalDate.of(2026, 10, 12), AllocationUnitType.SINGLE, identity,
                tokens, rank, rank, true, AllocationResolution.FIXED_WINNER, null, "v4", List.of(member));
    }

    private static PhysicalSeatEntity seat(long id, int number) {
        var seat = new PhysicalSeatEntity();
        seat.id = id;
        seat.seatNumber = number;
        return seat;
    }
}