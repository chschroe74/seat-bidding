package de.gigaworks.seatbidding.dto;

import de.gigaworks.seatbidding.persistence.RoundStatus;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "BiddingContext")
public record BiddingContextResponse(
        long roundId,
        RoundStatus status,
        Instant cutoffAt,
        String cutoffTimeZone,
        Instant serverTime,
        int seatCapacity,
        int startingBalance,
        int bidTotal,
        int availableToBid,
        List<DayBid> days) {
    
    public record DayBid(
            LocalDate date,
            DayOfWeek weekday,
            int tokens,
            int reservedSeatCount,
            int assignableSeatCapacity,
            String reservationDescription) {
        
    }
    
}
