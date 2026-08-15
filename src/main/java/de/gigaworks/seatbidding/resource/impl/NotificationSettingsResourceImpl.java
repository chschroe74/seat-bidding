package de.gigaworks.seatbidding.resource.impl;

import de.gigaworks.seatbidding.dto.NotificationSettingsResponse;
import de.gigaworks.seatbidding.dto.RegisterPushDeviceRequest;
import de.gigaworks.seatbidding.dto.SuppressBidRemindersRequest;
import de.gigaworks.seatbidding.dto.UpdateNotificationSettingsRequest;
import de.gigaworks.seatbidding.notification.NotificationSettingsService;
import de.gigaworks.seatbidding.notification.PushSubscriptionService;
import de.gigaworks.seatbidding.notification.ReminderSuppressionService;
import de.gigaworks.seatbidding.resource.NotificationSettingsResource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class NotificationSettingsResourceImpl implements NotificationSettingsResource {

    @Inject
    NotificationSettingsService settings;

    @Inject
    PushSubscriptionService subscriptions;

    @Inject
    ReminderSuppressionService suppressions;

    @Override
    public NotificationSettingsResponse get() {
        return settings.current();
    }

    @Override
    public NotificationSettingsResponse update(UpdateNotificationSettingsRequest request, String csrfToken) {
        return settings.update(request);
    }

    @Override
    public Response registerDevice(RegisterPushDeviceRequest request, String csrfToken) {
        var result = subscriptions.register(request);
        return Response.status(result.created() ? Response.Status.CREATED : Response.Status.OK)
                .location(result.location()).entity(result.device()).build();
    }

    @Override
    public Response removeDevice(long deviceId, String csrfToken) {
        subscriptions.remove(deviceId);
        return Response.noContent().build();
    }

    @Override
    public Response suppressCurrentRound(SuppressBidRemindersRequest request, String csrfToken) {
        suppressions.suppress(request.roundId());
        return Response.noContent().build();
    }

}