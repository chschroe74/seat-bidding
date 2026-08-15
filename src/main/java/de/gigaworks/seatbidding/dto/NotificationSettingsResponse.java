package de.gigaworks.seatbidding.dto;

import de.gigaworks.seatbidding.notification.ReminderStartWeekday;

import java.net.URI;
import java.time.Instant;
import java.util.List;

public record NotificationSettingsResponse(
        boolean bidRemindersEnabled,
        ReminderStartWeekday bidReminderStartWeekday,
        Schedule schedule,
        String webPushApplicationServerKey,
        CurrentRound currentRound,
        List<Device> devices) {

    public record Schedule(boolean systemEnabled, String localTime, String timeZone,
            List<ReminderStartWeekday> weekdays) {
    }

    public record CurrentRound(long roundId, Instant cutoffAt, boolean suppressed, boolean suppressionAvailable) {
    }

    public record Device(long id, String label, Instant registeredAt, Instant lastSeenAt,
            Instant lastSuccessfulPushAt) {
    }

    public record RegisteredDevice(Device device, URI location, boolean created) {
    }

}