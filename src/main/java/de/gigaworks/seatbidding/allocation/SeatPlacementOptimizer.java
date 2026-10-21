package de.gigaworks.seatbidding.allocation;

import de.gigaworks.seatbidding.persistence.PhysicalSeatEntity;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

@ApplicationScoped
public class SeatPlacementOptimizer {

    private static final BigInteger SATISFACTION_BASE = BigInteger.valueOf(16);

    private final SeatPriorityRandomSelector prioritySelector;
    private final SeatMappingRandomSelector mappingSelector;

    @Inject
    public SeatPlacementOptimizer(SeatPriorityRandomSelector prioritySelector,
            SeatMappingRandomSelector mappingSelector) {
        this.prioritySelector = prioritySelector;
        this.mappingSelector = mappingSelector;
    }

    public Placement place(List<RoundAllocation.Result> successfulUnits, List<PhysicalSeatEntity> seats,
            Map<Long, Map<Long, Integer>> ratings) {
        if (successfulUnits.size() > seats.size()) {
            throw new IllegalStateException("more successful units than active physical seats");
        }
        var orderedSeats = seats.stream().sorted(Comparator.comparingInt(value -> value.seatNumber)).toList();
        var order = priorityOrder(successfulUnits);
        var units = order.units();
        if (units.isEmpty()) {
            return new Placement(Map.of(), List.of(), order.auditValue(), null, Map.of());
        }

        var mappingDraw = mappingSelector.draw();
        var random = new SplittableRandom(mappingDraw.seed());
        BigInteger randomScale = BigInteger.valueOf((long) units.size() * Integer.MAX_VALUE + 1L);
        BigInteger[][] costs = new BigInteger[units.size()][orderedSeats.size()];
        for (int row = 0; row < units.size(); row++) {
            for (int column = 0; column < orderedSeats.size(); column++) {
                int satisfaction = satisfaction(units.get(row), orderedSeats.get(column), ratings);
                BigInteger reward = BigInteger.valueOf(satisfaction)
                        .multiply(SATISFACTION_BASE.pow(units.size() - row - 1));
                costs[row][column] = reward.negate().multiply(randomScale)
                        .add(BigInteger.valueOf(random.nextInt(Integer.MAX_VALUE)));
            }
        }

        int[] assignment = hungarian(costs);
        Map<String, PhysicalSeatEntity> byUnit = new HashMap<>();
        Map<String, String> satisfactionKeys = new HashMap<>();
        for (int row = 0; row < units.size(); row++) {
            var unit = units.get(row);
            var seat = orderedSeats.get(assignment[row]);
            byUnit.put(unit.fairnessIdentity(), seat);
            satisfactionKeys.put(unit.fairnessIdentity(), satisfactionKey(unit, seat, ratings));
        }
        return new Placement(Map.copyOf(byUnit), units.stream().map(RoundAllocation.Result::fairnessIdentity).toList(),
                order.auditValue(), mappingDraw.auditValue(), Map.copyOf(satisfactionKeys));
    }

    private PriorityOrder priorityOrder(List<RoundAllocation.Result> successfulUnits) {
        var byScore = successfulUnits.stream().collect(Collectors.groupingBy(RoundAllocation.Result::scoreTokens));
        var scores = byScore.keySet().stream().sorted(Comparator.reverseOrder()).toList();
        var ordered = new ArrayList<RoundAllocation.Result>();
        var audit = new ArrayList<String>();
        for (int score : scores) {
            var canonical = byScore.get(score).stream()
                    .sorted(Comparator.comparing(RoundAllocation.Result::fairnessIdentity)).toList();
            if (canonical.size() == 1) {
                ordered.add(canonical.getFirst());
                audit.add(score + ":fixed");
            }
            else {
                var draw = prioritySelector.permute(canonical);
                ordered.addAll(draw.values());
                audit.add(score + ":" + draw.auditValue());
            }
        }
        return new PriorityOrder(List.copyOf(ordered), String.join(",", audit));
    }

    private static int satisfaction(RoundAllocation.Result unit, PhysicalSeatEntity seat,
            Map<Long, Map<Long, Integer>> ratings) {
        var memberRatings = memberRatings(unit, seat, ratings);
        if (memberRatings.size() == 1) {
            return memberRatings.getFirst();
        }
        return memberRatings.getFirst() * 4 + memberRatings.getLast();
    }

    private static String satisfactionKey(RoundAllocation.Result unit, PhysicalSeatEntity seat,
            Map<Long, Map<Long, Integer>> ratings) {
        var memberRatings = memberRatings(unit, seat, ratings);
        return memberRatings.size() == 1 ? Integer.toString(memberRatings.getFirst())
                : "[" + memberRatings.getFirst() + "," + memberRatings.getLast() + "]";
    }

    private static List<Integer> memberRatings(RoundAllocation.Result unit, PhysicalSeatEntity seat,
            Map<Long, Map<Long, Integer>> ratings) {
        return unit.members().stream().map(member -> ratings
                .getOrDefault(member.employeeId(), Map.of()).getOrDefault(seat.id, 0)).sorted().toList();
    }

    static int[] hungarian(BigInteger[][] cost) {
        int rows = cost.length;
        if (rows == 0) {
            return new int[0];
        }
        int columns = cost[0].length;
        var u = new BigInteger[rows + 1];
        var v = new BigInteger[columns + 1];
        java.util.Arrays.fill(u, BigInteger.ZERO);
        java.util.Arrays.fill(v, BigInteger.ZERO);
        int[] p = new int[columns + 1];
        int[] way = new int[columns + 1];
        for (int i = 1; i <= rows; i++) {
            p[0] = i;
            int j0 = 0;
            var min = new BigInteger[columns + 1];
            var used = new boolean[columns + 1];
            do {
                used[j0] = true;
                int i0 = p[j0];
                BigInteger delta = null;
                int j1 = 0;
                for (int j = 1; j <= columns; j++) {
                    if (!used[j]) {
                        BigInteger current = cost[i0 - 1][j - 1].subtract(u[i0]).subtract(v[j]);
                        if (min[j] == null || current.compareTo(min[j]) < 0) {
                            min[j] = current;
                            way[j] = j0;
                        }
                        if (delta == null || min[j].compareTo(delta) < 0) {
                            delta = min[j];
                            j1 = j;
                        }
                    }
                }
                for (int j = 0; j <= columns; j++) {
                    if (used[j]) {
                        u[p[j]] = u[p[j]].add(delta);
                        v[j] = v[j].subtract(delta);
                    }
                    else if (min[j] != null) {
                        min[j] = min[j].subtract(delta);
                    }
                }
                j0 = j1;
            }
            while (p[j0] != 0);
            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            }
            while (j0 != 0);
        }
        int[] result = new int[rows];
        for (int j = 1; j <= columns; j++) {
            if (p[j] > 0) {
                result[p[j] - 1] = j - 1;
            }
        }
        return result;
    }

    private record PriorityOrder(List<RoundAllocation.Result> units, String auditValue) {
    }

    public record Placement(Map<String, PhysicalSeatEntity> byFairnessIdentity, List<String> priorityOrder,
            String prioritySelectionValue, String mappingSelectionValue, Map<String, String> satisfactionKeys) {
    }
}