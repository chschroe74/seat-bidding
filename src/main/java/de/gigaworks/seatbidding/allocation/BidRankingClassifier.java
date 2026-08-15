package de.gigaworks.seatbidding.allocation;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class BidRankingClassifier {

    private static final Comparator<RoundAllocation.Unit> UNIT_ORDER = Comparator
            .comparingInt(RoundAllocation.Unit::scoreTokens).reversed()
            .thenComparing(RoundAllocation.Unit::fairnessIdentity);

    public RoundAllocation.Problem classify(long roundId, int capacity,
            List<RoundAllocation.TargetDate> targetDates, RoundAllocation.Pairing pairing) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least one");
        }
        Map<Long, List<RoundAllocation.Unit>> unitsByDate = pairing.units().stream()
                .collect(Collectors.groupingBy(RoundAllocation.Unit::dateId));
        var dates = targetDates.stream().sorted(Comparator.comparing(RoundAllocation.TargetDate::targetDate)
                        .thenComparingLong(RoundAllocation.TargetDate::dateId))
                .map(date -> classifyDate(date, effectiveCapacity(date, capacity),
                        unitsByDate.getOrDefault(date.dateId(), List.of())))
                .toList();
        return new RoundAllocation.Problem(roundId, capacity, dates, pairing.auditJson());
    }

    private static RoundAllocation.ClassifiedDate classifyDate(RoundAllocation.TargetDate date,
            int capacity, List<RoundAllocation.Unit> units) {
        var byScore = new LinkedHashMap<Integer, List<RoundAllocation.Unit>>();
        units.stream().sorted(UNIT_ORDER)
                .forEach(unit -> byScore.computeIfAbsent(unit.scoreTokens(), _ -> new ArrayList<>()).add(unit));
        var classified = new ArrayList<RoundAllocation.ClassifiedUnit>();
        int unitsAbove = 0;
        int scoreRank = 0;
        int unresolvedSeats = 0;
        for (var entry : byScore.entrySet()) {
            scoreRank++;
            int groupEnd = unitsAbove + entry.getValue().size();
            RoundAllocation.Classification classification;
            String boundary = null;
            if (groupEnd <= capacity) {
                classification = RoundAllocation.Classification.FIXED_WINNER;
            }
            else if (unitsAbove >= capacity) {
                classification = RoundAllocation.Classification.FIXED_LOSER;
            }
            else {
                classification = RoundAllocation.Classification.BOUNDARY;
                unresolvedSeats = capacity - unitsAbove;
                boundary = "date:" + date.dateId() + ":score:" + entry.getKey();
            }
            for (var unit : entry.getValue()) {
                classified.add(new RoundAllocation.ClassifiedUnit(unit, scoreRank, classification, boundary));
            }
            unitsAbove = groupEnd;
        }
        return new RoundAllocation.ClassifiedDate(date.dateId(), date.targetDate(), date.reservationId(),
                date.reservedSeatCount(), capacity, unresolvedSeats, classified);
    }

    private static int effectiveCapacity(RoundAllocation.TargetDate date, int physicalCapacity) {
        int assignable = date.assignableSeatCapacity() < 0 ? physicalCapacity : date.assignableSeatCapacity();
        if (date.reservedSeatCount() < 0 || assignable < 0
                || date.reservedSeatCount() + assignable != physicalCapacity) {
            throw new IllegalArgumentException("reserved and assignable capacity must reconcile to physical capacity");
        }
        if ((date.reservationId() == null) != (date.reservedSeatCount() == 0)) {
            throw new IllegalArgumentException("reservation identity and reserved seat count must be consistent");
        }
        return assignable;
    }

}