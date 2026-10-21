package de.gigaworks.seatbidding.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.gigaworks.seatbidding.persistence.AllocationUnitType;
import de.gigaworks.seatbidding.persistence.AttendancePeriod;
import de.gigaworks.seatbidding.persistence.PhysicalSeatEntity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SeatPlacementOptimizerTest {

    private final SeatPlacementOptimizer optimizer = new SeatPlacementOptimizer(new FixedPrioritySelector(false),
            () -> new SeatMappingRandomSelector.Draw(1L, "mapping:1"));

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

    @Test
    void higherTokenUnitKeepsPreferredSeatDespiteLowerUnitsWorseAlternative() {
        var first = result("employee:1", 20, 1, 1);
        var second = result("employee:2", 10, 2, 2);
        var seats = List.of(seat(101, 1), seat(102, 2));

        var placement = optimizer.place(List.of(second, first), seats,
                Map.of(1L, Map.of(101L, 3, 102L, 2), 2L, Map.of(101L, 3)));

        assertEquals(101L, placement.byFairnessIdentity().get("employee:1").id);
        assertEquals(102L, placement.byFairnessIdentity().get("employee:2").id);
        assertEquals(List.of("employee:1", "employee:2"), placement.priorityOrder());
    }

    @Test
    void lowerPriorityPreferenceBreaksHigherPriorityIndifferenceGlobally() {
        var first = result("employee:1", 20, 1, 1);
        var second = result("employee:2", 10, 2, 2);
        var seats = List.of(seat(101, 1), seat(102, 2));

        var placement = optimizer.place(List.of(first, second), seats,
                Map.of(1L, Map.of(101L, 2, 102L, 2), 2L, Map.of(101L, 3)));

        assertEquals(102L, placement.byFairnessIdentity().get("employee:1").id);
        assertEquals(101L, placement.byFairnessIdentity().get("employee:2").id);
    }

    @Test
    void pairProtectsItsWorseRatedMemberBeforeALowerPrioritySingle() {
        var morning = new RoundAllocation.MemberResult(1, 1, 12,
                AttendancePeriod.MORNING_ONLY, (short) 1, 1);
        var afternoon = new RoundAllocation.MemberResult(2, 2, 8,
                AttendancePeriod.AFTERNOON_ONLY, (short) 2, 2);
        var pair = new RoundAllocation.Result(1, LocalDate.of(2026, 10, 12), AllocationUnitType.HALF_DAY_PAIR,
                "pair:1:2", 20, 1, 1, true, AllocationResolution.FIXED_WINNER, null, "v4",
                List.of(morning, afternoon));
        var single = result("employee:3", 10, 2, 3);

        var placement = optimizer.place(List.of(pair, single), List.of(seat(101, 1), seat(102, 2)),
                Map.of(
                        1L, Map.of(101L, 3, 102L, 3),
                        2L, Map.of(102L, 2),
                        3L, Map.of(102L, 3)));

        assertEquals(102L, placement.byFairnessIdentity().get("pair:1:2").id);
        assertEquals(101L, placement.byFairnessIdentity().get("employee:3").id);
        assertEquals("[2,3]", placement.satisfactionKeys().get("pair:1:2"));
    }

    @Test
    void equalScorePriorityIsDrawnIndependentlyOfPreferences() {
        var first = result("employee:1", 10, 1, 1);
        var second = result("employee:2", 10, 2, 2);
        var reversed = new SeatPlacementOptimizer(new FixedPrioritySelector(true),
                () -> new SeatMappingRandomSelector.Draw(1L, "mapping:1"));

        var placement = reversed.place(List.of(first, second), List.of(seat(101, 1), seat(102, 2)),
                Map.of(1L, Map.of(101L, 3), 2L, Map.of(101L, 3)));

        assertEquals(List.of("employee:2", "employee:1"), placement.priorityOrder());
        assertEquals(101L, placement.byFairnessIdentity().get("employee:2").id);
        assertEquals("10:priority:reverse", placement.prioritySelectionValue());
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

    private record FixedPrioritySelector(boolean reverse) implements SeatPriorityRandomSelector {

        @Override
        public <T> Permutation<T> permute(List<T> canonicalValues) {
            var values = new ArrayList<>(canonicalValues);
            if (reverse) {
                Collections.reverse(values);
            }
            return new Permutation<>(List.copyOf(values), reverse ? "priority:reverse" : "priority:canonical");
        }
    }
}