package de.gigaworks.seatbidding.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "CreateSeatReservation")
public record CreateSeatReservationRequest(
        @NotNull LocalDate date,
        @Min(1) int reservedSeatCount,
        String description) {
    
}
