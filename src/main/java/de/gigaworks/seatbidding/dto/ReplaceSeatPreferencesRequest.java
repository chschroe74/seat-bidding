package de.gigaworks.seatbidding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReplaceSeatPreferencesRequest(long roundId, long floorPlanVersionId, long preferenceRevision,
        @NotNull List<@Valid Preference> preferences) {

    public record Preference(long seatId, @Min(1) @Max(3) int stars) {
    }
}