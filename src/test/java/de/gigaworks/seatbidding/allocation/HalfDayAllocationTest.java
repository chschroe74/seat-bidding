package de.gigaworks.seatbidding.allocation;

import static de.gigaworks.seatbidding.persistence.AttendancePeriod.AFTERNOON_ONLY;
import static de.gigaworks.seatbidding.persistence.AttendancePeriod.FULL_DAY;
import static de.gigaworks.seatbidding.persistence.AttendancePeriod.MORNING_ONLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.gigaworks.seatbidding.persistence.AllocationUnitType;
import de.gigaworks.seatbidding.persistence.AttendancePeriod;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class HalfDayAllocationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 10);

    @Test
    void balancedGroupsPairInOppositeTokenOrder() {
        var pairing = pair(List.of(bid(1, 1, 30, MORNING_ONLY), bid(2, 2, 20, MORNING_ONLY),
                bid(3, 3, 15, AFTERNOON_ONLY), bid(4, 4, 5, AFTERNOON_ONLY)));

        assertEquals(2, pairing.units().size());
        assertEquals(List.of("PAIR:1:4", "PAIR:2:3"),
                pairing.units().stream().map(RoundAllocation.Unit::fairnessIdentity).sorted().toList());
        assertTrue(pairing.units().stream().allMatch(unit -> unit.unitType() == AllocationUnitType.HALF_DAY_PAIR));
    }

    @Test
    void oversizedSideSelectsHighestAndRandomizesOnlyExactBoundaryTie() {
        var selector = new IndexedPairingSelector(1);
        var pairing = pair(selector, List.of(bid(1, 1, 30, MORNING_ONLY), bid(2, 2, 20, MORNING_ONLY),
                bid(3, 3, 20, MORNING_ONLY), bid(4, 4, 10, MORNING_ONLY),
                bid(5, 5, 15, AFTERNOON_ONLY), bid(6, 6, 5, AFTERNOON_ONLY)));

        assertEquals(1, selector.invocations);
        var pairedEmployees = pairing.units().stream()
                .filter(unit -> unit.unitType() == AllocationUnitType.HALF_DAY_PAIR)
                .flatMap(unit -> unit.members().stream()).map(RoundAllocation.Member::employeeId).toList();
        assertTrue(pairedEmployees.contains(1L));
        assertTrue(pairedEmployees.contains(2L) ^ pairedEmployees.contains(3L));
        assertFalse(pairedEmployees.contains(4L));
        assertEquals(2, pairing.units().stream().filter(unit -> unit.unitType() == AllocationUnitType.SINGLE).count());
    }

    @Test
    void unmatchedHalfDayBidCompetesAsSingleAndPairScoreIsRankedAsAUnit() {
        var solution = solve(1, List.of(bid(1, 1, 8, MORNING_ONLY), bid(2, 2, 7, AFTERNOON_ONLY),
                bid(3, 3, 14, MORNING_ONLY), bid(4, 4, 10, FULL_DAY)));

        var winner = solution.results().stream().filter(RoundAllocation.Result::assigned).findFirst().orElseThrow();
        assertEquals(AllocationUnitType.HALF_DAY_PAIR, winner.unitType());
        assertEquals(21, winner.scoreTokens());
        assertEquals(2, winner.members().size());
        assertTrue(solution.results().stream().anyMatch(result -> result.unitType() == AllocationUnitType.SINGLE
                && result.members().getFirst().attendancePeriod() == MORNING_ONLY && !result.assigned()));
    }

    @Test
    void pairsAreIndivisibleAtCapacityBoundary() {
        var solution = solve(1, List.of(bid(1, 1, 10, MORNING_ONLY), bid(2, 2, 10, AFTERNOON_ONLY),
                bid(3, 3, 20, FULL_DAY)));

        assertEquals(1, solution.results().stream().filter(RoundAllocation.Result::assigned).count());
        assertTrue(solution.results().stream()
                .filter(result -> result.unitType() == AllocationUnitType.HALF_DAY_PAIR)
                .allMatch(result -> result.members().size() == 2));
    }

    @Test
    void pairFairnessIdentityIsUnorderedAndStableAcrossRoleReversal() {
        var first = pair(List.of(bid(1, 10, 10, MORNING_ONLY), bid(2, 20, 10, AFTERNOON_ONLY)));
        var second = new HalfDayPairingEngine(new IndexedPairingSelector(0)).pair(
                List.of(new RoundAllocation.TargetDate(101, MONDAY.plusDays(1))),
                List.of(new RoundAllocation.Bid(101, MONDAY.plusDays(1), 3, 10, 10, AFTERNOON_ONLY),
                        new RoundAllocation.Bid(101, MONDAY.plusDays(1), 4, 20, 10, MORNING_ONLY)));
        assertEquals("PAIR:10:20", first.units().getFirst().fairnessIdentity());
        assertEquals(first.units().getFirst().fairnessIdentity(), second.units().getFirst().fairnessIdentity());
    }

    @Test
    void canonicalPairingAndAllocationIgnoreInputOrder() {
        var bids = new ArrayList<>(List.of(bid(1, 1, 10, MORNING_ONLY), bid(2, 2, 10, MORNING_ONLY),
                bid(3, 3, 10, AFTERNOON_ONLY), bid(4, 4, 10, FULL_DAY)));
        var first = solve(2, bids);
        Collections.reverse(bids);
        var reordered = solve(2, bids);
        assertEquals(first.inputFingerprint(), reordered.inputFingerprint());
        assertEquals(first.selectedSolutionFingerprint(), reordered.selectedSolutionFingerprint());
    }

    @Test
    void differentPartnersProduceDifferentFairnessIdentities() {
        var first = pair(List.of(bid(1, 1, 10, MORNING_ONLY), bid(2, 2, 10, AFTERNOON_ONLY)));
        var second = pair(List.of(bid(1, 1, 10, MORNING_ONLY), bid(3, 3, 10, AFTERNOON_ONLY)));
        assertNotEquals(first.units().getFirst().fairnessIdentity(), second.units().getFirst().fairnessIdentity());
    }

    @Test
    void noComplementaryBiddersRemainSingles() {
        var pairing = pair(List.of(bid(1, 1, 12, MORNING_ONLY), bid(2, 2, 8, MORNING_ONLY),
                bid(3, 3, 5, FULL_DAY)));
        assertTrue(pairing.units().stream().allMatch(unit -> unit.unitType() == AllocationUnitType.SINGLE));
    }

    @Test
    void reservationsReduceCapacityWithoutChangingPairing() {
        var bids = List.of(bid(1, 1, 10, MORNING_ONLY), bid(2, 2, 10, AFTERNOON_ONLY),
                bid(3, 3, 15, FULL_DAY));
        var unreservedDate = List.of(new RoundAllocation.TargetDate(100, MONDAY));
        var reservedDate = List.of(new RoundAllocation.TargetDate(100, MONDAY, 7L, 1, 1));
        var engine = new HalfDayPairingEngine(new IndexedPairingSelector(0));
        var first = engine.pair(unreservedDate, bids);
        var second = engine.pair(reservedDate, bids);
        assertEquals(first.units(), second.units());
        var reservedSolution = new GlobalFairnessOptimizer(new IndexedSelector(0)).solve(
                new BidRankingClassifier().classify(1, 2, reservedDate, second));
        assertEquals(1, reservedSolution.results().stream().filter(RoundAllocation.Result::assigned).count());
    }

    private static RoundAllocation.Pairing pair(List<RoundAllocation.Bid> bids) {
        return pair(new IndexedPairingSelector(0), bids);
    }

    private static RoundAllocation.Pairing pair(IndexedPairingSelector selector, List<RoundAllocation.Bid> bids) {
        return new HalfDayPairingEngine(selector).pair(
                List.of(new RoundAllocation.TargetDate(100, MONDAY)), bids);
    }

    private static RoundAllocation.Solution solve(int capacity, List<RoundAllocation.Bid> bids) {
        var dates = List.of(new RoundAllocation.TargetDate(100, MONDAY));
        var pairing = new HalfDayPairingEngine(new IndexedPairingSelector(0)).pair(dates, bids);
        var problem = new BidRankingClassifier().classify(9, capacity, dates, pairing);
        return new GlobalFairnessOptimizer(new IndexedSelector(0)).solve(problem);
    }

    private static RoundAllocation.Bid bid(long bidId, long employeeId, int tokens, AttendancePeriod period) {
        return new RoundAllocation.Bid(100, MONDAY, bidId, employeeId, tokens, period);
    }

    private static final class IndexedPairingSelector implements PairingRandomSelector {
        private final int index;
        private int invocations;
        private IndexedPairingSelector(int index) { this.index = index; }
        @Override public <T> RandomSelector.Draw<T> select(List<T> values) {
            invocations++;
            int selected = Math.floorMod(index, values.size());
            return new RandomSelector.Draw<>(values.get(selected), "pair-index:" + selected);
        }
    }

    private static final class IndexedSelector implements RandomSelector {
        private final int index;
        private IndexedSelector(int index) { this.index = index; }
        @Override public <T> Draw<T> select(List<T> values) {
            int selected = Math.floorMod(index, values.size());
            return new Draw<>(values.get(selected), "capacity-index:" + selected);
        }
    }

}