package de.gigaworks.seatbidding.notification;

import de.gigaworks.seatbidding.auth.SecretDigests;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class SeatOfferDispatchService {

    @Inject SeatOfferDispatchPersistence persistence;
    @Inject SeatOfferPayloadBuilder payloads;
    @Inject WebPushTransport transport;
    @Inject Clock clock;

    void afterCommit(@Observes(during = TransactionPhase.AFTER_SUCCESS) SeatOfferNotificationRequested request) {
        try {
            deliver(request.candidateId());
        }
        catch (RuntimeException exception) {
            log.error("operation=seat-offer-dispatch outcome=failed candidateId={}", request.candidateId(), exception);
        }
    }

    void deliver(long candidateId) {
        var claim = persistence.claim(candidateId, clock.instant()).orElse(null);
        if (claim == null) {
            return;
        }
        String payload = payloads.build(candidateId, claim.roundId(), claim.date(), claim.period());
        String topic = SecretDigests.sha256("seat-offer:" + candidateId).substring(0, 32);
        long remainingSeconds = Math.max(0, Duration.between(clock.instant(), claim.deadline()).toSeconds());
        int timeToLiveSeconds = (int) Math.min(Integer.MAX_VALUE, remainingSeconds);
        int accepted = 0;
        int failed = 0;
        for (var subscription : claim.subscriptions()) {
            WebPushTransport.SendResult result;
            try {
                result = transport.send(new WebPushTransport.Message(claim.roundId(), subscription.endpoint(),
                        subscription.p256dh(), subscription.auth(), payload, topic, timeToLiveSeconds, true));
            }
            catch (RuntimeException exception) {
                result = WebPushTransport.SendResult.temporary(null);
            }
            var attemptedAt = clock.instant();
            persistence.recordAttempt(claim.dispatchId(), subscription.id(), result, attemptedAt);
            if (result.outcome() == PushDeliveryOutcome.ACCEPTED) {
                accepted++;
            }
            else {
                failed++;
            }
        }
        persistence.complete(claim.dispatchId(), accepted, failed, clock.instant());
    }
}