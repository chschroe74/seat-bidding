package de.gigaworks.seatbidding.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AllocationEngineTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 10);

    @Test
    void scenarioASimpleSingleDayBoundaryTieUsesInjectedSelector() {
        var selector = new IndexedSelector(2);
        var solution = solve(1, selector, dates(1), List.of(
                bid(0, 1, 1, 10), bid(0, 2, 2, 10), bid(0, 3, 3, 10)));

        assertEquals(1, assigned(solution).size());
        assertEquals(3, solution.equivalentSolutionCount());
        assertEquals("index:2", solution.randomSelectionValue());
        assertEquals(1, selector.invocations);
    }

    @Test
    void scenarioBThreeEquivalentEmployeesAcrossThreeDaysReceiveOneEach() {
        var solution = solve(1, new IndexedSelector(0), dates(3), equivalentBids(3, 3));

        assertEquals(List.of(1, 1, 1), solution.objective().sortedTieWinCounts());
        assertEquals(3, solution.objective().distinctTieWinners());
    }

    @Test
    void scenarioCFiveOpportunitiesProduceTwoTwoOne() {
        var solution = solve(1, new IndexedSelector(0), dates(5), equivalentBids(5, 3));

        assertEquals(List.of(1, 2, 2), solution.objective().sortedTieWinCounts());
    }

    @Test
    void scenarioDSixOpportunitiesProduceTwoEach() {
        var solution = solve(2, new IndexedSelector(0), dates(3), equivalentBids(3, 3));

        assertEquals(6, solution.objective().filledUnresolvedSlots());
        assertEquals(List.of(2, 2, 2), solution.objective().sortedTieWinCounts());
    }

    @Test
    void scenarioEConstrainedMondayOpportunityEmergesFromGlobalObjectives() {
        var bids = List.of(
                bid(0, 1, 1, 10), bid(0, 2, 2, 10), bid(0, 3, 3, 10),
                bid(1, 4, 2, 10), bid(1, 5, 3, 10),
                bid(2, 6, 2, 10), bid(2, 7, 3, 10));
        var solution = solve(1, new IndexedSelector(0), dates(3), bids);

        assertTrue(solution.results().stream().anyMatch(result -> result.bidId() == 1 && result.assigned()));
        assertEquals(List.of(1, 1, 1), solution.objective().sortedTieWinCounts());
    }

    @Test
    void scenarioFTokenRankingCannotBeOverridden() {
        var solution = solve(2, new IndexedSelector(0), dates(1), List.of(
                bid(0, 1, 1, 20), bid(0, 2, 2, 10), bid(0, 3, 3, 10), bid(0, 4, 4, 5)));
        Map<Long, RoundAllocation.Result> byBid = byBid(solution);

        assertEquals(AllocationResolution.FIXED_WINNER, byBid.get(1L).resolution());
        assertTrue(byBid.get(1L).assigned());
        assertTrue(byBid.get(2L).assigned() ^ byBid.get(3L).assigned());
        assertEquals(AllocationResolution.FIXED_LOSER, byBid.get(4L).resolution());
        assertFalse(byBid.get(4L).assigned());
        assertEquals(1, byBid.get(1L).tokenRank());
        assertEquals(2, byBid.get(2L).tokenRank());
        assertEquals(3, byBid.get(4L).tokenRank());
    }

    @Test
    void scenarioGMultipleUnresolvedSeatsNeverDuplicateAnEmployeeOnADate() {
        var solution = solve(2, new IndexedSelector(0), dates(1), List.of(
                bid(0, 1, 1, 10), bid(0, 2, 2, 10), bid(0, 3, 3, 10)));

        assertEquals(2, assigned(solution).size());
        assertEquals(2, assigned(solution).stream().map(RoundAllocation.Result::employeeId).distinct().count());
    }

    @Test
    void scenarioHDifferentBoundaryGroupsRemainDateSpecific() {
        var solution = solve(2, new IndexedSelector(0), dates(2), List.of(
                bid(0, 1, 1, 20), bid(0, 2, 2, 10), bid(0, 3, 3, 10),
                bid(1, 4, 2, 30), bid(1, 5, 3, 30), bid(1, 6, 4, 30)));

        assertEquals("date:100:tokens:10", byBid(solution).get(2L).boundaryTieGroup());
        assertEquals("date:101:tokens:30", byBid(solution).get(4L).boundaryTieGroup());
        assertEquals(3, solution.objective().filledUnresolvedSlots());
        assertEquals(3, solution.objective().distinctTieWinners());
    }

    @Test
    void scenarioIMoreCandidatesThanSeatsStillFillsEverySeat() {
        var solution = solve(2, new IndexedSelector(0), dates(1), List.of(
                bid(0, 1, 1, 10), bid(0, 2, 2, 10), bid(0, 3, 3, 10), bid(0, 4, 4, 10)));

        assertEquals(2, assigned(solution).size());
        assertEquals(2, solution.objective().distinctTieWinners());
    }

    @Test
    void scenarioJAdditionalWinsUseLexicographicMaxMinFairness() {
        var solution = solve(1, new IndexedSelector(0), dates(5), equivalentBids(5, 3));

        assertEquals(3, solution.objective().distinctTieWinners());
        assertEquals(List.of(1, 2, 2), solution.objective().sortedTieWinCounts());
    }

    @Test
    void scenarioKSelectorChoosesOnlyAmongCanonicalEquivalentGlobalOptima() {
        var inputs = equivalentBids(3, 3);
        var first = solve(1, new IndexedSelector(0), dates(3), inputs);
        var last = solve(1, new IndexedSelector(Integer.MAX_VALUE), dates(3), inputs);

        assertEquals(first.objective(), last.objective());
        assertNotEquals(first.selectedSolutionFingerprint(), last.selectedSolutionFingerprint());
        assertTrue(first.equivalentSolutionCount() > 1);
    }

    @Test
    void scenarioOInputOrderDoesNotAffectCanonicalSelectionOrFingerprints() {
        var canonicalDates = dates(3);
        var canonicalBids = new ArrayList<>(equivalentBids(3, 3));
        var first = solve(1, new IndexedSelector(0), canonicalDates, canonicalBids);
        Collections.reverse(canonicalDates);
        Collections.rotate(canonicalBids, 4);
        var reordered = solve(1, new IndexedSelector(0), canonicalDates, canonicalBids);

        assertEquals(first.inputFingerprint(), reordered.inputFingerprint());
        assertEquals(first.selectedSolutionFingerprint(), reordered.selectedSolutionFingerprint());
        assertEquals(first.results(), reordered.results());
    }

    @Test
    void scenarioPNoBoundaryTieDoesNotInvokeSelectorButStillProducesAuditData() {
        var selector = new IndexedSelector(0);
        var solution = solve(2, selector, dates(1), List.of(bid(0, 1, 1, 20), bid(0, 2, 2, 10)));

        assertEquals(0, selector.invocations);
        assertNull(solution.randomSelectionValue());
        assertEquals(64, solution.inputFingerprint().length());
        assertEquals(64, solution.selectedSolutionFingerprint().length());
        assertTrue(solution.results().stream().allMatch(result -> result.resolution() == AllocationResolution.FIXED_WINNER));
    }

    @Test
    void scenarioQSurplusCapacityAssignsEveryBidderWithoutInventingAssignments() {
        var solution = solve(4, new IndexedSelector(0), dates(1), List.of(bid(0, 1, 1, 20), bid(0, 2, 2, 10)));

        assertEquals(2, solution.results().size());
        assertEquals(2, assigned(solution).size());
        assertEquals(0, solution.objective().filledUnresolvedSlots());
    }

    @Test
    void scenarioRReservationReducesCapacityBeforeRankingAndChangesCanonicalInput() {
        var bids = List.of(bid(0, 1, 1, 20), bid(0, 2, 2, 10), bid(0, 3, 3, 10), bid(0, 4, 4, 10));
        var reservedDate = List.of(new RoundAllocation.TargetDate(100, MONDAY, 91L, 1, 3));
        var reservationAware = solve(4, new IndexedSelector(0), reservedDate, bids);
        var unreserved = solve(4, new IndexedSelector(0), dates(1), bids);

        assertEquals(3, assigned(reservationAware).size());
        assertEquals(4, assigned(unreserved).size());
        assertNotEquals(reservationAware.inputFingerprint(), unreserved.inputFingerprint());
        assertTrue(reservationAware.results().stream()
                .allMatch(result -> RoundAllocation.ALGORITHM_VERSION.equals(result.algorithmVersion())));
    }

    @Test
    void scenarioSAllPhysicalSeatsReservedProducesOnlyFixedLosers() {
        var selector = new IndexedSelector(0);
        var reservedDate = List.of(new RoundAllocation.TargetDate(100, MONDAY, 92L, 4, 0));
        var solution = solve(4, selector, reservedDate,
                List.of(bid(0, 1, 1, 20), bid(0, 2, 2, 10)));

        assertTrue(assigned(solution).isEmpty());
        assertEquals(0, solution.objective().filledUnresolvedSlots());
        assertEquals(0, selector.invocations);
        assertTrue(solution.results().stream()
                .allMatch(result -> result.resolution() == AllocationResolution.FIXED_LOSER));
    }

    private static RoundAllocation.Solution solve(int capacity, IndexedSelector selector,
            List<RoundAllocation.TargetDate> dates, List<RoundAllocation.Bid> bids) {
        var classifier = new BidRankingClassifier();
        return new GlobalFairnessOptimizer(selector).solve(classifier.classify(7, capacity, dates, bids));
    }

    private static List<RoundAllocation.TargetDate> dates(int count) {
        var dates = new ArrayList<RoundAllocation.TargetDate>();
        for (int index = 0; index < count; index++) {
            dates.add(new RoundAllocation.TargetDate(100 + index, MONDAY.plusDays(index)));
        }
        return dates;
    }

    private static List<RoundAllocation.Bid> equivalentBids(int dateCount, int employeeCount) {
        var bids = new ArrayList<RoundAllocation.Bid>();
        long bidId = 1;
        for (int date = 0; date < dateCount; date++) {
            for (int employee = 1; employee <= employeeCount; employee++) {
                bids.add(bid(date, bidId++, employee, 10));
            }
        }
        return bids;
    }

    private static RoundAllocation.Bid bid(int date, long bidId, long employeeId, int tokens) {
        return new RoundAllocation.Bid(100 + date, MONDAY.plusDays(date), bidId, employeeId, tokens);
    }

    private static List<RoundAllocation.Result> assigned(RoundAllocation.Solution solution) {
        return solution.results().stream().filter(RoundAllocation.Result::assigned).toList();
    }

    private static Map<Long, RoundAllocation.Result> byBid(RoundAllocation.Solution solution) {
        var results = new HashMap<Long, RoundAllocation.Result>();
        solution.results().forEach(result -> results.put(result.bidId(), result));
        return results;
    }

    private static final class IndexedSelector implements RandomSelector {
        private final int requestedIndex;
        private int invocations;

        private IndexedSelector(int requestedIndex) {
            this.requestedIndex = requestedIndex;
        }

        @Override
        public <T> Draw<T> select(List<T> canonicalValues) {
            invocations++;
            int index = Math.floorMod(requestedIndex, canonicalValues.size());
            return new Draw<>(canonicalValues.get(index), "index:" + index);
        }
    }
}
