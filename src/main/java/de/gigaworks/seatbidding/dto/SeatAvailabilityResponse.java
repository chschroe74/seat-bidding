package de.gigaworks.seatbidding.dto;

import de.gigaworks.seatbidding.persistence.AttendancePeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SeatAvailabilityResponse(
        long roundId,
        LocalDate date,
        AttendancePeriod attendancePeriod,
        long floorPlanVersionId,
        Instant serverTime,
        List<AvailableSeat> availableSeats) {

    public record AvailableSeat(long physicalSeatId, int seatNumber) {
    }
}