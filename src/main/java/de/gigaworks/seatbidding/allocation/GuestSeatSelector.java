package de.gigaworks.seatbidding.allocation;

import de.gigaworks.seatbidding.persistence.PhysicalSeatEntity;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class GuestSeatSelector {

    private GuestSeatSelector() {
    }

    static List<PhysicalSeatEntity> select(List<PhysicalSeatEntity> seats,
            Collection<PhysicalSeatEntity> employeeSeats, Map<Long, Integer> ratings, int reservedSeatCount) {
        var remaining = seats.stream().filter(seat -> !employeeSeats.contains(seat))
                .sorted(Comparator.comparingInt((PhysicalSeatEntity seat) -> ratings.getOrDefault(seat.id, 0))
                        .reversed().thenComparingInt(seat -> seat.seatNumber))
                .toList();
        if (remaining.size() < reservedSeatCount) {
            throw new IllegalStateException("not enough unused floor-plan seats for reserved guests");
        }
        return remaining.subList(0, reservedSeatCount);
    }
}