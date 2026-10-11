package de.gigaworks.seatbidding.round;

import de.gigaworks.seatbidding.exception.ConfigurationException;
import de.gigaworks.seatbidding.notification.ReminderSchedule;
import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.persistence.FloorPlanRepository;
import de.gigaworks.seatbidding.persistence.FloorPlanVersionSeatRepository;
import de.gigaworks.seatbidding.persistence.RoundStatus;
import com.interaso.webpush.VapidKeys;
import io.quarkus.runtime.StartupEvent;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import jakarta.transaction.Transactional;
import java.time.Instant;

@ApplicationScoped
public class ConfigurationValidator {

    @Inject
    SeatBiddingConfiguration configuration;

    @Inject
    RoundSchedule schedule;

    @Inject
    ReminderSchedule reminderSchedule;

    @Inject
    FloorPlanRepository floorPlans;

    @Inject
    FloorPlanVersionSeatRepository floorPlanSeats;

    @Inject
    BiddingRoundRepository rounds;

    @Transactional
    void validate(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE) StartupEvent ignored) {
        if (configuration.tokensPerRound() < 0) {
            throw new ConfigurationException("Configuration property {} must be non-negative", "tokens-per-round");
        }
        if (configuration.carryOverCap() < 0) {
            throw new ConfigurationException("Configuration property {} must be non-negative", "carry-over-cap");
        }
        if (configuration.seatCapacity() < 1) {
            throw new ConfigurationException("Configuration property {} must be at least one", "seat-capacity");
        }
        if (configuration.lockTimeout().isZero() || configuration.lockTimeout().isNegative()) {
            throw new ConfigurationException("Configuration property {} must be positive", "lock-timeout");
        }
        schedule.nextCutoff(configuration.scheduler().cron(), configuration.timeZone(), Instant.now());
        schedule.nextCutoff(configuration.reminders().schedule().cron(), configuration.timeZone(), Instant.now());
        reminderSchedule.localTime(configuration.reminders().schedule().cron());
        validateDuration("reminders.web-push.time-to-live", configuration.reminders().webPush().timeToLive());
        validateDuration("reminders.web-push.connect-timeout", configuration.reminders().webPush().connectTimeout());
        validateDuration("reminders.web-push.request-timeout", configuration.reminders().webPush().requestTimeout());
        validateDuration("floor-plan.parse-timeout", configuration.floorPlan().parseTimeout());
        if (configuration.floorPlan().maxActiveSeats() < 1 || configuration.floorPlan().maxUploadBytes() < 1
                || configuration.floorPlan().maxElements() < 1 || configuration.floorPlan().maxPathCommands() < 1) {
            throw new ConfigurationException("Floor-plan limits must be positive");
        }
        validatePersistedFloorPlanLimit();
        validateWebPush();
    }

    private void validatePersistedFloorPlanLimit() {
        int maximum = configuration.floorPlan().maxActiveSeats();
        var versionIds = new java.util.HashSet<Long>();
        floorPlans.findSingleton().ifPresent(plan -> {
            if (plan.activeVersion != null) {
                versionIds.add(plan.activeVersion.id);
            }
            if (plan.pendingVersion != null) {
                versionIds.add(plan.pendingVersion.id);
            }
        });
        rounds.find("status in ?1 and floorPlanVersion is not null", java.util.List.of(RoundStatus.OPEN,
                RoundStatus.PROCESSING)).list().forEach(round -> versionIds.add(round.floorPlanVersion.id));
        if (versionIds.stream().anyMatch(versionId -> floorPlanSeats.countActiveForVersion(versionId) > maximum)) {
            throw new ConfigurationException(
                    "Configuration property {} is below an active, pending, or in-progress floor-plan seat count",
                    "floor-plan.max-active-seats");
        }
    }

    private void validateWebPush() {
        var push = configuration.reminders().webPush();
        if (!(push.vapidSubject().startsWith("mailto:") || push.vapidSubject().startsWith("https://"))) {
            throw new ConfigurationException("Configuration property {} must use mailto: or https:",
                    "reminders.web-push.vapid-subject");
        }
        try {
            VapidKeys.fromUncompressedBytes(push.vapidPublicKey(), push.vapidPrivateKey());
            Math.toIntExact(push.timeToLive().toSeconds());
        }
        catch (RuntimeException exception) {
            throw new ConfigurationException(exception, "Invalid Web Push VAPID keys or time-to-live configuration");
        }
    }

    private static void validateDuration(String property, java.time.Duration value) {
        if (value.isZero() || value.isNegative()) {
            throw new ConfigurationException("Configuration property {} must be positive", property);
        }
    }

}