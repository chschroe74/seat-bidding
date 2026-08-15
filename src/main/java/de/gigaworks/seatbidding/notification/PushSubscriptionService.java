package de.gigaworks.seatbidding.notification;

import de.gigaworks.seatbidding.auth.EmployeeIdentityService;
import de.gigaworks.seatbidding.auth.SecretDigests;
import de.gigaworks.seatbidding.dto.NotificationSettingsResponse;
import de.gigaworks.seatbidding.dto.RegisterPushDeviceRequest;
import de.gigaworks.seatbidding.exception.ApplicationProblem;
import de.gigaworks.seatbidding.persistence.WebPushSubscriptionEntity;
import de.gigaworks.seatbidding.persistence.WebPushSubscriptionRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.net.URI;
import java.time.Clock;

@ApplicationScoped
public class PushSubscriptionService {

    @Inject
    EmployeeIdentityService identity;

    @Inject
    WebPushSubscriptionRepository subscriptions;

    @Inject
    PushEndpointValidator validator;

    @Inject
    Clock clock;

    @Transactional
    public NotificationSettingsResponse.RegisteredDevice register(RegisterPushDeviceRequest request) {
        var employee = identity.resolve();
        var now = clock.instant();
        var validated = validator.validate(request.endpoint(), request.keys().p256dh(), request.keys().auth(),
                request.expirationTime(), request.deviceLabel(), now);
        String hash = SecretDigests.sha256(validated.endpoint());
        var existing = subscriptions.findByEndpointHash(hash);
        if (existing.isPresent() && !existing.get().employee.id.equals(employee.id)) {
            throw ApplicationProblem.conflict("PUSH_ENDPOINT_OWNERSHIP_CONFLICT", "Device registration unavailable",
                    "This push subscription cannot be registered to the current account.");
        }
        boolean created = existing.isEmpty();
        var subscription = existing.orElseGet(() -> {
            var value = new WebPushSubscriptionEntity();
            value.employee = employee;
            value.endpointHash = hash;
            return value;
        });
        subscription.endpoint = validated.endpoint();
        subscription.p256dhKey = validated.p256dh();
        subscription.authKey = validated.auth();
        subscription.expiresAt = validated.expiresAt();
        subscription.deviceLabel = validated.deviceLabel();
        subscription.status = PushSubscriptionStatus.ACTIVE;
        subscription.lastSeenAt = now;
        subscription.invalidatedAt = null;
        if (created) {
            subscriptions.persist(subscription);
        }
        subscriptions.flush();
        URI location = URI.create("/api/settings/notifications/devices/" + subscription.id);
        return new NotificationSettingsResponse.RegisteredDevice(NotificationSettingsService.device(subscription),
                location, created);
    }

    @Transactional
    public void remove(long deviceId) {
        var employee = identity.resolve();
        var subscription = subscriptions.findActiveOwned(deviceId, employee.id)
                .orElseThrow(() -> ApplicationProblem.notFound("PUSH_DEVICE_NOT_FOUND",
                        "The registered device could not be found."));
        subscription.deactivate(PushSubscriptionStatus.USER_REMOVED, clock.instant());
    }

}