package de.gigaworks.seatbidding.dto;

import jakarta.validation.constraints.NotNull;

public record SuppressBidRemindersRequest(@NotNull Long roundId) {
}