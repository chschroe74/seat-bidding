package de.gigaworks.seatbidding.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "FloorPlan")
public record FloorPlanResponse(long roundId, long floorPlanVersionId, String svgUrl, ViewBox viewBox,
        Instant cutoffAt, Instant serverTime, long preferenceRevision, boolean effectiveForCurrentRound,
        List<Seat> seats) {

    public record ViewBox(BigDecimal minX, BigDecimal minY, BigDecimal width, BigDecimal height) {
    }

    public record Seat(long id, int number, BigDecimal normalizedX, BigDecimal normalizedY, int stars) {
    }
}