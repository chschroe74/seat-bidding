package de.gigaworks.seatbidding.notification;

import de.gigaworks.seatbidding.persistence.AttendancePeriod;
import de.gigaworks.seatbidding.persistence.SeatOfferDeliveryAttemptEntity;
import de.gigaworks.seatbidding.persistence.SeatOfferDeliveryAttemptRepository;
import de.gigaworks.seatbidding.persistence.SeatOfferNotificationDispatchRepository;
import de.gigaworks.seatbidding.persistence.WebPushSubscriptionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class SeatOfferDispatchPersistence {

    @Inject SeatOfferNotificationDispatchRepository dispatches;
    @Inject SeatOfferDeliveryAttemptRepository attempts;
    @Inject WebPushSubscriptionRepository subscriptions;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Optional<Claim> claim(long candidateId, Instant now) {
        var dispatch = dispatches.findForCandidateForUpdate(candidateId).orElse(null);
        if (dispatch == null || !"PENDING".equals(dispatch.status)) {
            return Optional.empty();
        }
        var active = subscriptions.findActiveForEmployee(dispatch.employee.id, now);
        dispatch.status = "PROCESSING";
        dispatch.subscriptionCount = active.size();
        if (active.isEmpty()) {
            dispatch.status = "COMPLETED";
            dispatch.completedAt = now;
            return Optional.empty();
        }
        var candidate = dispatch.candidate;
        List<SubscriptionSnapshot> snapshots = active.stream()
                .map(value -> new SubscriptionSnapshot(value.id, value.endpoint, value.p256dhKey, value.authKey))
                .toList();
        return Optional.of(new Claim(dispatch.id, candidate.roundDate.round.id, candidate.roundDate.targetDate,
                candidate.attendancePeriod, candidate.offer.deadline, snapshots));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordAttempt(long dispatchId, long subscriptionId, WebPushTransport.SendResult result,
            Instant attemptedAt) {
        var dispatch = dispatches.findById(dispatchId);
        var subscription = subscriptions.findById(subscriptionId);
        if (dispatch == null || subscription == null) {
            throw new IllegalStateException("Seat-offer dispatch or subscription disappeared");
        }
        var attempt = new SeatOfferDeliveryAttemptEntity();
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
        if (dispatch == null || !"PROCESSING".equals(dispatch.status)) {
            return;
        }
        dispatch.acceptedCount = accepted;
        dispatch.failedCount = failed;
        dispatch.completedAt = completedAt;
        dispatch.status = accepted == dispatch.subscriptionCount ? "COMPLETED"
                : accepted == 0 ? "FAILED" : "PARTIAL";
    }

    public record Claim(long dispatchId, long roundId, LocalDate date, AttendancePeriod period, Instant deadline,
            List<SubscriptionSnapshot> subscriptions) {
    }

    public record SubscriptionSnapshot(long id, String endpoint, String p256dh, String auth) {
    }
}