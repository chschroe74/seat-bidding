package de.gigaworks.seatbidding.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminFloorPlanResponse(long stateRevision, int permanentNumberWatermark, Version activeVersion,
        Version pendingVersion, boolean pendingDeactivation, Version draft, List<String> warnings) {

    public record Version(long id, long versionNumber, String status, long draftRevision, String svgUrl,
            FloorPlanResponse.ViewBox viewBox, List<Seat> seats) {
    }

    public record Seat(long id, int number, BigDecimal normalizedX, BigDecimal normalizedY, boolean inherited,
            String state) {
    }
}