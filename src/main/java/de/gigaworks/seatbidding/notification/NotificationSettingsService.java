package de.gigaworks.seatbidding.notification;

import de.gigaworks.seatbidding.auth.EmployeeIdentityService;
import de.gigaworks.seatbidding.dto.NotificationSettingsResponse;
import de.gigaworks.seatbidding.dto.UpdateNotificationSettingsRequest;
import de.gigaworks.seatbidding.persistence.BidReminderSuppressionRepository;
import de.gigaworks.seatbidding.persistence.BidRepository;
import de.gigaworks.seatbidding.persistence.EmployeeNotificationSettingsEntity;
import de.gigaworks.seatbidding.persistence.EmployeeNotificationSettingsRepository;
import de.gigaworks.seatbidding.persistence.WebPushSubscriptionEntity;
import de.gigaworks.seatbidding.persistence.WebPushSubscriptionRepository;
import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.round.SeatBiddingConfiguration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;

@ApplicationScoped
public class NotificationSettingsService {

    @Inject
    EmployeeIdentityService identity;

    @Inject
    EmployeeNotificationSettingsRepository settings;

    @Inject
    WebPushSubscriptionRepository subscriptions;

    @Inject
    BidReminderSuppressionRepository suppressions;

    @Inject
    BiddingRoundRepository rounds;

    @Inject
    BidRepository bids;

    @Inject
    SeatBiddingConfiguration configuration;

    @Inject
    ReminderSchedule reminderSchedule;

    @Inject
    Clock clock;

    @Transactional
    public NotificationSettingsResponse current() {
        var employee = identity.resolve();
        return response(employee.id, settings.findForEmployee(employee.id).orElse(null), clock.instant());
    }

    @Transactional
    public NotificationSettingsResponse update(UpdateNotificationSettingsRequest request) {
        var employee = identity.resolve();
        var value = settings.findForEmployee(employee.id).orElseGet(() -> {
            var created = new EmployeeNotificationSettingsEntity();
            created.employee = employee;
            settings.persist(created);
            return created;
        });
        value.bidRemindersEnabled = request.bidRemindersEnabled();
        value.bidReminderStartWeekday = request.bidReminderStartWeekday();
        settings.flush();
        return response(employee.id, value, clock.instant());
    }

    private NotificationSettingsResponse response(long employeeId, EmployeeNotificationSettingsEntity value,
            Instant now) {
        boolean enabled = value != null && value.bidRemindersEnabled;
        ReminderStartWeekday weekday = value == null ? ReminderStartWeekday.MONDAY : value.bidReminderStartWeekday;
        var devices = subscriptions.findActiveForEmployee(employeeId, now).stream()
                .map(NotificationSettingsService::device).toList();
        var currentRound = rounds.findOpen().map(round -> {
            boolean suppressed = suppressions.exists(round.id, employeeId);
            boolean positiveBid = bids.hasPositiveBid(round.id, employeeId);
            return new NotificationSettingsResponse.CurrentRound(round.id, round.cutoffAt, suppressed,
                    !suppressed && !positiveBid && now.isBefore(round.cutoffAt));
        }).orElse(null);
        var schedule = new NotificationSettingsResponse.Schedule(configuration.reminders().schedule().enabled(),
                reminderSchedule.localTime(configuration.reminders().schedule().cron()).toString(),
                configuration.timeZone().getId(), Arrays.asList(ReminderStartWeekday.values()));
        return new NotificationSettingsResponse(enabled, weekday, schedule,
                configuration.reminders().webPush().vapidPublicKey(), currentRound, devices);
    }

    public static NotificationSettingsResponse.Device device(WebPushSubscriptionEntity subscription) {
        return new NotificationSettingsResponse.Device(subscription.id, subscription.deviceLabel,
                subscription.createdAt, subscription.lastSeenAt, subscription.lastSuccessfulPushAt);
    }

}