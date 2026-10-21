package de.gigaworks.seatbidding.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.gigaworks.seatbidding.persistence.PhysicalSeatEntity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GuestSeatSelectorTest {

    @Test
    void selectsOnlyUnusedSeatsByRatingThenPermanentNumber() {
        var seats = List.of(seat(10, 1), seat(20, 2), seat(30, 3), seat(40, 4), seat(50, 5));

        var selected = GuestSeatSelector.select(seats, List.of(seats.get(2)),
                Map.of(10L, 1, 20L, 2, 30L, 3, 40L, 2), 3);

        assertEquals(List.of(20L, 40L, 10L), selected.stream().map(value -> value.id).toList());
    }

    @Test
    void rejectsMoreGuestsThanUnusedSeats() {
        var seats = List.of(seat(10, 1), seat(20, 2));

        assertThrows(IllegalStateException.class,
                () -> GuestSeatSelector.select(seats, List.of(seats.getFirst()), Map.of(), 2));
    }

    private static PhysicalSeatEntity seat(long id, int number) {
        var seat = new PhysicalSeatEntity();
        seat.id = id;
        seat.seatNumber = number;
        return seat;
    }
}