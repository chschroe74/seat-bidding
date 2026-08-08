package de.gigaworks.seatbidding.round;

import de.gigaworks.seatbidding.exception.ConfigurationException;
import io.quarkus.runtime.StartupEvent;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import java.time.Instant;

@ApplicationScoped
public class ConfigurationValidator {
    
    @Inject
    SeatBiddingConfiguration configuration;

    @Inject
    RoundSchedule schedule;
    
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
        schedule.nextCutoff(configuration.scheduler().cron(), configuration.scheduler().timeZone(), Instant.now());
    }
    
}
