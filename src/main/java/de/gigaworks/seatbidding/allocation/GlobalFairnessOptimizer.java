package de.gigaworks.seatbidding.allocation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class GlobalFairnessOptimizer {

    private static final Comparator<RoundAllocation.ClassifiedBid> BID_ID_ORDER = Comparator
            .comparingLong(RoundAllocation.ClassifiedBid::bidId);

    private final RandomSelector randomSelector;

    @Inject
    public GlobalFairnessOptimizer(RandomSelector randomSelector) {
        this.randomSelector = randomSelector;
    }

    public RoundAllocation.Solution solve(RoundAllocation.Problem problem) {
        var boundaryDates = problem.dates().stream().filter(date -> date.unresolvedSeats() > 0).toList();
        var alternatives = new ArrayList<Alternative>();
        enumerate(boundaryDates, 0, new LinkedHashMap<>(), alternatives);
        if (alternatives.isEmpty()) {
            alternatives.add(new Alternative(Map.of()));
        }

        RoundAllocation.Objective bestObjective = null;
        var optimal = new ArrayList<Alternative>();
        for (var alternative : alternatives) {
            var objective = objective(problem, alternative);
            int comparison = bestObjective == null ? 1 : compare(objective, bestObjective);
            if (comparison > 0) {
                bestObjective = objective;
                optimal.clear();
                optimal.add(alternative);
            }
            else if (comparison == 0) {
                optimal.add(alternative);
            }
        }
        optimal.sort(Comparator.comparing(Alternative::canonicalKey));

        Alternative selected;
        String randomValue = null;
        if (optimal.size() > 1) {
            var draw = randomSelector.select(List.copyOf(optimal));
            selected = draw.value();
            randomValue = draw.auditValue();
        }
        else {
            selected = optimal.getFirst();
        }
        var results = materialize(problem, selected);
        return new RoundAllocation.Solution(results, bestObjective, AllocationFingerprints.input(problem),
                AllocationFingerprints.solution(results), randomValue, optimal.size());
    }

    private static void enumerate(List<RoundAllocation.ClassifiedDate> dates, int dateIndex,
            Map<Long, Set<Long>> winnersByDate, List<Alternative> alternatives) {
        if (dateIndex == dates.size()) {
            alternatives.add(new Alternative(winnersByDate));
            return;
        }
        var date = dates.get(dateIndex);
        var candidates = date.bids().stream()
                .filter(bid -> bid.classification() == RoundAllocation.Classification.BOUNDARY)
                .sorted(BID_ID_ORDER).toList();
        var combinations = new ArrayList<Set<Long>>();
        combinations(candidates, date.unresolvedSeats(), 0, new ArrayList<>(), combinations);
        for (var combination : combinations) {
            var next = new LinkedHashMap<>(winnersByDate);
            next.put(date.dateId(), combination);
            enumerate(dates, dateIndex + 1, next, alternatives);
        }
    }

    private static void combinations(List<RoundAllocation.ClassifiedBid> candidates, int required, int index,
            List<Long> selected, List<Set<Long>> combinations) {
        if (selected.size() == required) {
            combinations.add(Set.copyOf(selected));
            return;
        }
        int stillRequired = required - selected.size();
        for (int current = index; current <= candidates.size() - stillRequired; current++) {
            selected.add(candidates.get(current).bidId());
            combinations(candidates, required, current + 1, selected, combinations);
            selected.removeLast();
        }
    }

    private static RoundAllocation.Objective objective(RoundAllocation.Problem problem, Alternative alternative) {
        Set<Long> participants = problem.dates().stream().flatMap(date -> date.bids().stream())
                .filter(bid -> bid.classification() == RoundAllocation.Classification.BOUNDARY)
                .map(RoundAllocation.ClassifiedBid::employeeId).collect(Collectors.toCollection(HashSet::new));
        Map<Long, Long> employeeByBid = problem.dates().stream().flatMap(date -> date.bids().stream())
                .collect(Collectors.toMap(RoundAllocation.ClassifiedBid::bidId,
                        RoundAllocation.ClassifiedBid::employeeId));
        var counts = new HashMap<Long, Integer>();
        participants.forEach(employee -> counts.put(employee, 0));
        int filled = 0;
        for (var winners : alternative.winnersByDate().values()) {
            filled += winners.size();
            winners.forEach(bidId -> counts.merge(employeeByBid.get(bidId), 1, Integer::sum));
        }
        int distinct = (int) counts.values().stream().filter(count -> count > 0).count();
        var sortedCounts = counts.values().stream().sorted().toList();
        return new RoundAllocation.Objective(filled, distinct, sortedCounts);
    }

    private static int compare(RoundAllocation.Objective left, RoundAllocation.Objective right) {
        int comparison = Integer.compare(left.filledUnresolvedSlots(), right.filledUnresolvedSlots());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(left.distinctTieWinners(), right.distinctTieWinners());
        if (comparison != 0) {
            return comparison;
        }
        for (int index = 0; index < left.sortedTieWinCounts().size(); index++) {
            comparison = Integer.compare(left.sortedTieWinCounts().get(index), right.sortedTieWinCounts().get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static List<RoundAllocation.Result> materialize(
            RoundAllocation.Problem problem, Alternative selected) {
        var results = new ArrayList<RoundAllocation.Result>();
        for (var date : problem.dates()) {
            Set<Long> boundaryWinners = selected.winnersByDate().getOrDefault(date.dateId(), Set.of());
            var ordered = date.bids().stream().sorted(Comparator
                    .comparingInt((RoundAllocation.ClassifiedBid bid) -> displayGroup(bid, boundaryWinners))
                    .thenComparing(Comparator.comparingInt(RoundAllocation.ClassifiedBid::tokens).reversed())
                    .thenComparingLong(RoundAllocation.ClassifiedBid::bidId)).toList();
            int finalRank = 0;
            for (var bid : ordered) {
                boolean tieWinner = boundaryWinners.contains(bid.bidId());
                AllocationResolution resolution = switch (bid.classification()) {
                    case FIXED_WINNER ->
                            AllocationResolution.FIXED_WINNER;
                    case FIXED_LOSER ->
                            AllocationResolution.FIXED_LOSER;
                    case BOUNDARY ->
                            tieWinner
                                    ? AllocationResolution.GLOBAL_TIE_WINNER : AllocationResolution.GLOBAL_TIE_LOSER;
                };
                boolean assigned = resolution == AllocationResolution.FIXED_WINNER
                        || resolution == AllocationResolution.GLOBAL_TIE_WINNER;
                results.add(new RoundAllocation.Result(date.dateId(), date.targetDate(), bid.bidId(), bid.employeeId(),
                        bid.tokens(), bid.tokenRank(), ++finalRank, assigned, resolution, bid.boundaryTieGroup(),
                        RoundAllocation.ALGORITHM_VERSION));
            }
        }
        return List.copyOf(results);
    }

    private static int displayGroup(RoundAllocation.ClassifiedBid bid, Set<Long> boundaryWinners) {
        return switch (bid.classification()) {
            case FIXED_WINNER ->
                    0;
            case BOUNDARY ->
                    boundaryWinners.contains(bid.bidId()) ? 1 : 2;
            case FIXED_LOSER ->
                    3;
        };
    }

    private record Alternative(
            Map<Long, Set<Long>> winnersByDate) {

        private Alternative {
            var copy = new LinkedHashMap<Long, Set<Long>>();
            winnersByDate.forEach((date, winners) -> copy.put(date, Set.copyOf(winners)));
            winnersByDate = Map.copyOf(copy);
        }

        private String canonicalKey() {
            return winnersByDate.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + ":" + entry.getValue().stream().sorted()
                            .map(String::valueOf).collect(Collectors.joining(",")))
                    .collect(Collectors.joining(";"));
        }

    }

}
