package de.gigaworks.seatbidding.dto;

import java.time.Instant;
import java.time.LocalDate;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "SeatReservation")
public record SeatReservationResponse(
        long id,
        LocalDate date,
        int reservedSeatCount,
        int physicalSeatCapacity,
        String description,
        boolean mutable,
        Instant cutoffAt,
        String roundStatus) {
    
}
