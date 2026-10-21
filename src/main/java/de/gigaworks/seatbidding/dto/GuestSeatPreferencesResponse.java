package de.gigaworks.seatbidding.dto;

import java.time.Instant;
import java.util.List;

public record GuestSeatPreferencesResponse(long floorPlanVersionId, long preferenceRevision, Instant cutoffAt,
        Instant serverTime, boolean effectiveForCurrentRound, List<Seat> seats) {

    public record Seat(long id, int number, int stars) {
    }
}