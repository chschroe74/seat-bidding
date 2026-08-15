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
        RoundAllocation.Objective best = null;
        var optimal = new ArrayList<Alternative>();
        for (var alternative : alternatives) {
            var objective = objective(problem, alternative);
            int comparison = best == null ? 1 : compare(objective, best);
            if (comparison > 0) {
                best = objective;
                optimal.clear();
                optimal.add(alternative);
            }
            else if (comparison == 0) {
                optimal.add(alternative);
            }
        }
        optimal.sort(Comparator.comparing(Alternative::canonicalKey));
        Alternative selected = optimal.getFirst();
        String selectionValue = null;
        if (optimal.size() > 1) {
            var draw = randomSelector.select(List.copyOf(optimal));
            selected = draw.value();
            selectionValue = draw.auditValue();
        }
        var results = materialize(problem, selected);
        return new RoundAllocation.Solution(results, best, problem.pairingAudit(),
                AllocationFingerprints.input(problem), AllocationFingerprints.solution(results),
                selectionValue, optimal.size());
    }

    private static void enumerate(List<RoundAllocation.ClassifiedDate> dates, int index,
            Map<Long, Set<String>> winners, List<Alternative> alternatives) {
        if (index == dates.size()) {
            alternatives.add(new Alternative(winners));
            return;
        }
        var date = dates.get(index);
        var candidates = date.units().stream()
                .filter(unit -> unit.classification() == RoundAllocation.Classification.BOUNDARY)
                .sorted(Comparator.comparing(unit -> unit.unit().fairnessIdentity())).toList();
        var combinations = new ArrayList<Set<String>>();
        combinations(candidates, date.unresolvedSeats(), 0, new ArrayList<>(), combinations);
        for (var combination : combinations) {
            var next = new LinkedHashMap<>(winners);
            next.put(date.dateId(), combination);
            enumerate(dates, index + 1, next, alternatives);
        }
    }

    private static void combinations(List<RoundAllocation.ClassifiedUnit> candidates, int required, int index,
            List<String> selected, List<Set<String>> combinations) {
        if (selected.size() == required) {
            combinations.add(Set.copyOf(selected));
            return;
        }
        for (int current = index; current <= candidates.size() - (required - selected.size()); current++) {
            selected.add(candidates.get(current).unit().fairnessIdentity());
            combinations(candidates, required, current + 1, selected, combinations);
            selected.removeLast();
        }
    }

    private static RoundAllocation.Objective objective(RoundAllocation.Problem problem, Alternative alternative) {
        Set<String> identities = problem.dates().stream().flatMap(date -> date.units().stream())
                .filter(unit -> unit.classification() == RoundAllocation.Classification.BOUNDARY)
                .map(unit -> unit.unit().fairnessIdentity()).collect(Collectors.toCollection(HashSet::new));
        var counts = new HashMap<String, Integer>();
        identities.forEach(identity -> counts.put(identity, 0));
        int filled = 0;
        for (var winners : alternative.winnersByDate().values()) {
            filled += winners.size();
            winners.forEach(identity -> counts.merge(identity, 1, Integer::sum));
        }
        return new RoundAllocation.Objective(filled,
                (int) counts.values().stream().filter(value -> value > 0).count(),
                counts.values().stream().sorted().toList());
    }

    private static int compare(RoundAllocation.Objective left, RoundAllocation.Objective right) {
        int result = Integer.compare(left.filledUnresolvedSlots(), right.filledUnresolvedSlots());
        if (result != 0) {
            return result;
        }
        result = Integer.compare(left.distinctTieWinners(), right.distinctTieWinners());
        if (result != 0) {
            return result;
        }
        for (int index = 0; index < left.sortedTieWinCounts().size(); index++) {
            result = Integer.compare(left.sortedTieWinCounts().get(index), right.sortedTieWinCounts().get(index));
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private static List<RoundAllocation.Result> materialize(RoundAllocation.Problem problem, Alternative selected) {
        var results = new ArrayList<RoundAllocation.Result>();
        for (var date : problem.dates()) {
            Set<String> boundaryWinners = selected.winnersByDate().getOrDefault(date.dateId(), Set.of());
            var ordered = date.units().stream().sorted(Comparator
                    .comparingInt((RoundAllocation.ClassifiedUnit unit) -> displayGroup(unit, boundaryWinners))
                    .thenComparing(Comparator.comparingInt(
                            (RoundAllocation.ClassifiedUnit unit) -> unit.unit().scoreTokens()).reversed())
                    .thenComparing(unit -> unit.unit().fairnessIdentity())).toList();
            int unitRank = 0;
            int displayRank = 0;
            for (var classified : ordered) {
                boolean tieWinner = boundaryWinners.contains(classified.unit().fairnessIdentity());
                var resolution = switch (classified.classification()) {
                    case FIXED_WINNER -> AllocationResolution.FIXED_WINNER;
                    case FIXED_LOSER -> AllocationResolution.FIXED_LOSER;
                    case BOUNDARY -> tieWinner ? AllocationResolution.GLOBAL_TIE_WINNER
                            : AllocationResolution.GLOBAL_TIE_LOSER;
                };
                boolean assigned = resolution == AllocationResolution.FIXED_WINNER
                        || resolution == AllocationResolution.GLOBAL_TIE_WINNER;
                var members = new ArrayList<RoundAllocation.MemberResult>();
                for (var member : classified.unit().members().stream()
                        .sorted(Comparator.comparingInt(RoundAllocation.Member::memberOrder)).toList()) {
                    members.add(new RoundAllocation.MemberResult(member.bidId(), member.employeeId(), member.tokens(),
                            member.attendancePeriod(), member.memberOrder(), ++displayRank));
                }
                results.add(new RoundAllocation.Result(date.dateId(), date.targetDate(), classified.unit().unitType(),
                        classified.unit().fairnessIdentity(), classified.unit().scoreTokens(), classified.scoreRank(),
                        ++unitRank, assigned, resolution, classified.boundaryTieGroup(),
                        RoundAllocation.ALGORITHM_VERSION, members));
            }
        }
        return List.copyOf(results);
    }

    private static int displayGroup(RoundAllocation.ClassifiedUnit unit, Set<String> winners) {
        return switch (unit.classification()) {
            case FIXED_WINNER -> 0;
            case BOUNDARY -> winners.contains(unit.unit().fairnessIdentity()) ? 1 : 2;
            case FIXED_LOSER -> 3;
        };
    }

    private record Alternative(Map<Long, Set<String>> winnersByDate) {
        private Alternative {
            var copy = new LinkedHashMap<Long, Set<String>>();
            winnersByDate.forEach((date, winners) -> copy.put(date, Set.copyOf(winners)));
            winnersByDate = Map.copyOf(copy);
        }

        String canonicalKey() {
            return winnersByDate.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + ":" + entry.getValue().stream().sorted()
                            .collect(Collectors.joining(",")))
                    .collect(Collectors.joining(";"));
        }
    }

}