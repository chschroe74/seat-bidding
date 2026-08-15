package de.gigaworks.seatbidding.notification;

import de.gigaworks.seatbidding.auth.SecretDigests;
import lombok.extern.slf4j.Slf4j;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;

@ApplicationScoped
@Slf4j
public class ReminderDispatchService {

    @Inject
    ReminderEligibilityService eligibility;

    @Inject
    ReminderDispatchPersistence persistence;

    @Inject
    BidReminderPayloadBuilder payloads;

    @Inject
    WebPushTransport transport;

    @Inject
    Clock clock;

    public Summary dispatch(Instant scheduledFor) {
        var candidates = eligibility.select(scheduledFor).orElse(null);
        if (candidates == null) {
            return new Summary(0, 0, 0, 0);
        }
        int dispatchCount = 0;
        int attemptCount = 0;
        int acceptedCount = 0;
        int failureCount = 0;
        for (long employeeId : candidates.employeeIds()) {
            try {
                var claim = persistence.claim(candidates.roundId(), employeeId, candidates.businessDate(),
                        candidates.scheduledFor()).orElse(null);
                if (claim == null) {
                    continue;
                }
                dispatchCount++;
                var result = deliver(claim);
                attemptCount += result.attempted();
                acceptedCount += result.accepted();
                failureCount += result.failed();
            }
            catch (RuntimeException exception) {
                log.error("operation=bid-reminder-dispatch outcome=employee-failed roundId={} employeeId={} businessDate={}",
                        candidates.roundId(), employeeId, candidates.businessDate(), exception);
            }
        }
        return new Summary(dispatchCount, attemptCount, acceptedCount, failureCount);
    }

    private DeliverySummary deliver(ReminderDispatchPersistence.Claim claim) {
        String payload = payloads.build(claim.roundId());
        String topic = SecretDigests.sha256("bid-reminder:" + claim.roundId() + ':' + claim.businessDate())
                .substring(0, 32);
        int accepted = 0;
        int failed = 0;
        for (var subscription : claim.subscriptions()) {
            WebPushTransport.SendResult result;
            try {
                result = transport.send(new WebPushTransport.Message(claim.roundId(), subscription.endpoint(),
                        subscription.p256dh(), subscription.auth(), payload, topic));
            }
            catch (RuntimeException exception) {
                result = WebPushTransport.SendResult.temporary(null);
            }
            Instant attemptedAt = clock.instant();
            persistence.recordAttempt(claim.dispatchId(), subscription.id(), result, attemptedAt);
            if (result.outcome() == PushDeliveryOutcome.ACCEPTED) {
                accepted++;
            }
            else {
                failed++;
            }
        }
        persistence.complete(claim.dispatchId(), accepted, failed, clock.instant());
        return new DeliverySummary(claim.subscriptions().size(), accepted, failed);
    }

    public record Summary(int dispatches, int attempts, int accepted, int failed) {
    }

    private record DeliverySummary(int attempted, int accepted, int failed) {
    }

}