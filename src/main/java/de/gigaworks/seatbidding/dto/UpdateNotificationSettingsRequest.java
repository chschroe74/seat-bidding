package de.gigaworks.seatbidding.dto;

import de.gigaworks.seatbidding.notification.ReminderStartWeekday;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationSettingsRequest(
        @NotNull Boolean bidRemindersEnabled,
        @NotNull ReminderStartWeekday bidReminderStartWeekday) {
}