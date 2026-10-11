package de.gigaworks.seatbidding.allocation;

import de.gigaworks.seatbidding.persistence.PhysicalSeatEntity;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

@ApplicationScoped
public class SeatPlacementOptimizer {

    public Placement place(List<RoundAllocation.Result> successfulUnits, List<PhysicalSeatEntity> seats,
            Map<Long, Map<Long, Integer>> ratings) {
        if (successfulUnits.size() > seats.size()) {
            throw new IllegalStateException("more successful units than active physical seats");
        }
        var units = successfulUnits.stream().sorted(Comparator.comparingInt(RoundAllocation.Result::scoreTokens)
                .reversed().thenComparingInt(RoundAllocation.Result::finalRank)).toList();
        var orderedSeats = seats.stream().sorted(Comparator.comparingInt(value -> value.seatNumber)).toList();
        int ratedMembers = (int) units.stream().flatMap(value -> value.members().stream())
                .filter(member -> ratings.containsKey(member.employeeId())).count();
        BigInteger preferenceBase = BigInteger.valueOf(Math.max(2, ratedMembers + 1L));
        BigInteger tokenBase = BigInteger.valueOf(16);
        BigInteger tokenScale = tokenBase.pow(units.size());
        long seed = new SecureRandom().nextLong();
        var random = new SplittableRandom(seed);
        BigInteger randomScale = BigInteger.valueOf((long) Math.max(1, units.size()) * Integer.MAX_VALUE + 1L);
        BigInteger[][] costs = new BigInteger[units.size()][orderedSeats.size()];
        for (int row = 0; row < units.size(); row++) {
            for (int column = 0; column < orderedSeats.size(); column++) {
                var unit = units.get(row);
                var seat = orderedSeats.get(column);
                BigInteger primary = BigInteger.ZERO;
                var memberRatings = new ArrayList<Integer>();
                for (var member : unit.members()) {
                    Map<Long, Integer> employeeRatings = ratings.get(member.employeeId());
                    int rating = employeeRatings == null ? 0 : employeeRatings.getOrDefault(seat.id, 0);
                    memberRatings.add(rating);
                    if (employeeRatings != null) {
                        primary = primary.add(switch (rating) {
                            case 0 -> preferenceBase.pow(2);
                            case 1 -> preferenceBase;
                            case 2 -> BigInteger.ONE;
                            default -> BigInteger.ZERO;
                        });
                    }
                }
                memberRatings.sort(Integer::compareTo);
                int tuple = memberRatings.size() == 1 ? memberRatings.getFirst() * 5
                        : memberRatings.getFirst() * 4 + memberRatings.getLast();
                BigInteger tokenReward = BigInteger.valueOf(tuple)
                        .multiply(tokenBase.pow(units.size() - row - 1));
                BigInteger deterministic = primary.multiply(tokenScale).subtract(tokenReward);
                costs[row][column] = deterministic.multiply(randomScale)
                        .add(BigInteger.valueOf(random.nextInt(Integer.MAX_VALUE)));
            }
        }
        int[] assignment = hungarian(costs);
        Map<String, PhysicalSeatEntity> byUnit = new HashMap<>();
        for (int row = 0; row < units.size(); row++) {
            byUnit.put(units.get(row).fairnessIdentity(), orderedSeats.get(assignment[row]));
        }
        return new Placement(Map.copyOf(byUnit), Long.toUnsignedString(seed, 16));
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

    public record Placement(Map<String, PhysicalSeatEntity> byFairnessIdentity, String selectionValue) {
    }
}