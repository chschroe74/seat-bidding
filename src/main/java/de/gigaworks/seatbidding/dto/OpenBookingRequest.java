package de.gigaworks.seatbidding.dto;

import de.gigaworks.seatbidding.persistence.AttendancePeriod;
import jakarta.validation.constraints.NotNull;

public record OpenBookingRequest(@NotNull AttendancePeriod attendancePeriod, Long physicalSeatId) {
}