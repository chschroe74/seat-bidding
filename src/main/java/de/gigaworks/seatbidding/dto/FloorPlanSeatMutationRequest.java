package de.gigaworks.seatbidding.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record FloorPlanSeatMutationRequest(long draftRevision,
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal normalizedX,
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal normalizedY) {
}