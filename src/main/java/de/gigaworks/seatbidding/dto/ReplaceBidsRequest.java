package de.gigaworks.seatbidding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ReplaceBidsRequest")
public record ReplaceBidsRequest(
        @Min(1) long roundId,
        @NotNull @Size(max = 5) List<@Valid BidValue> bids) {
    
    public record BidValue(
            @NotNull LocalDate date,
            @Min(0) @Max(Integer.MAX_VALUE) int tokens) {
        
    }
    
}

