package de.gigaworks.seatbidding.dto;

import java.time.Instant;
import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "SeatReservationList")
public record SeatReservationListResponse(
        Instant serverTime,
        String timeZone,
        List<SeatReservationResponse> reservations) {
    
}
