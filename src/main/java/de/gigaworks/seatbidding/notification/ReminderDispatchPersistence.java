package de.gigaworks.seatbidding.notification;

import de.gigaworks.seatbidding.persistence.BidReminderDispatchEntity;
import de.gigaworks.seatbidding.persistence.BidReminderDispatchRepository;
import de.gigaworks.seatbidding.persistence.BidReminderSuppressionRepository;
import de.gigaworks.seatbidding.persistence.BidRepository;
import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.persistence.EmployeeNotificationSettingsRepository;
import de.gigaworks.seatbidding.persistence.EmployeeRepository;
import de.gigaworks.seatbidding.persistence.RoundStatus;
import de.gigaworks.seatbidding.persistence.WebPushDeliveryAttemptEntity;
import de.gigaworks.seatbidding.persistence.WebPushDeliveryAttemptRepository;
import de.gigaworks.seatbidding.persistence.WebPushSubscriptionRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ReminderDispatchPersistence {

    @Inject
    BiddingRoundRepository rounds;

    @Inject
    EmployeeRepository employees;

    @Inject
    EmployeeNotificationSettingsRepository settings;

    @Inject
    WebPushSubscriptionRepository subscriptions;

    @Inject
    BidReminderSuppressionRepository suppressions;

    @Inject
    BidRepository bids;

    @Inject
    BidReminderDispatchRepository dispatches;

    @Inject
    WebPushDeliveryAttemptRepository attempts;

    @Inject
    EntityManager entityManager;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Optional<Claim> claim(long roundId, long employeeId, LocalDate businessDate, Instant scheduledFor) {
        var round = rounds.findById(roundId);
        var employee = employees.findById(employeeId);
        if (round == null || employee == null || round.status != RoundStatus.OPEN
                || !scheduledFor.isBefore(round.cutoffAt)
                || !businessDate.equals(scheduledFor.atZone(ZoneId.of(round.scheduleZone)).toLocalDate())) {
            return Optional.empty();
        }
        var preference = settings.findForEmployee(employeeId).orElse(null);
        if (preference == null || !preference.bidRemindersEnabled
                || !preference.bidReminderStartWeekday.hasStarted(businessDate.getDayOfWeek())
                || suppressions.exists(roundId, employeeId) || bids.hasPositiveBid(roundId, employeeId)) {
            return Optional.empty();
        }
        var active = subscriptions.findActiveForEmployee(employeeId, scheduledFor);
        if (active.isEmpty()) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        var insertedIds = (List<Number>) entityManager.createNativeQuery("""
                INSERT INTO bid_reminder_dispatch
                    (round_id, employee_id, business_date, scheduled_for, status, subscription_count,
                     accepted_count, failed_count, created_at)
                VALUES (?1, ?2, ?3, ?4, 'PROCESSING', ?5, 0, 0, ?6)
                ON CONFLICT (round_id, employee_id, business_date) DO NOTHING
                RETURNING id
                """).setParameter(1, roundId).setParameter(2, employeeId).setParameter(3, businessDate)
                .setParameter(4, scheduledFor).setParameter(5, active.size()).setParameter(6, Instant.now())
                .getResultList();
        if (insertedIds.isEmpty()) {
            return Optional.empty();
        }
        long dispatchId = insertedIds.getFirst().longValue();
        var snapshots = active.stream().map(subscription -> new SubscriptionSnapshot(subscription.id,
                subscription.endpoint, subscription.p256dhKey, subscription.authKey)).toList();
        return Optional.of(new Claim(dispatchId, roundId, employeeId, businessDate, snapshots));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordAttempt(long dispatchId, long subscriptionId, WebPushTransport.SendResult result,
            Instant attemptedAt) {
        var dispatch = dispatches.findById(dispatchId);
        var subscription = subscriptions.findById(subscriptionId);
        if (dispatch == null || subscription == null) {
            throw new IllegalStateException("Reminder dispatch or subscription disappeared before attempt recording");
        }
        var attempt = new WebPushDeliveryAttemptEntity();
        attempt.dispatch = dispatch;
        attempt.subscription = subscription;
        attempt.outcome = result.outcome();
        attempt.providerStatus = result.providerStatus();
        attempt.attemptedAt = attemptedAt;
        attempts.persist(attempt);
        if (result.outcome() == PushDeliveryOutcome.ACCEPTED) {
            subscription.lastSuccessfulPushAt = attemptedAt;
        }
        else if (result.outcome() == PushDeliveryOutcome.PERMANENT_FAILURE
                && subscription.status == PushSubscriptionStatus.ACTIVE) {
            subscription.deactivate(PushSubscriptionStatus.INVALID, attemptedAt);
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void complete(long dispatchId, int accepted, int failed, Instant completedAt) {
        var dispatch = dispatches.findById(dispatchId);
        if (dispatch == null || dispatch.status != ReminderDispatchStatus.PROCESSING) {
            return;
        }
        dispatch.acceptedCount = accepted;
        dispatch.failedCount = failed;
        dispatch.completedAt = completedAt;
        dispatch.status = accepted == dispatch.subscriptionCount ? ReminderDispatchStatus.COMPLETED
                : accepted == 0 ? ReminderDispatchStatus.FAILED : ReminderDispatchStatus.PARTIAL;
    }

    public record Claim(long dispatchId, long roundId, long employeeId, LocalDate businessDate,
            List<SubscriptionSnapshot> subscriptions) {
    }

    public record SubscriptionSnapshot(long id, String endpoint, String p256dh, String auth) {
    }

}