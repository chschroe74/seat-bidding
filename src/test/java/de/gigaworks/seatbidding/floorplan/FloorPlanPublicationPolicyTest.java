package de.gigaworks.seatbidding.floorplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.gigaworks.seatbidding.persistence.BiddingRoundEntity;
import de.gigaworks.seatbidding.persistence.CapacitySource;
import de.gigaworks.seatbidding.persistence.FloorPlanVersionEntity;
import de.gigaworks.seatbidding.persistence.RoundStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FloorPlanPublicationPolicyTest {

    private final FloorPlanPublicationPolicy policy = new FloorPlanPublicationPolicy();
    private final Instant now = Instant.parse("2026-10-11T10:00:00Z");

    @Test
    void immediatelyActivatesAnEqualCapacityConfigurationRound() {
        var decision = policy.decide(round(CapacitySource.CONFIGURATION, 12), 12, now);

        assertTrue(decision.immediate());
        assertEquals("CURRENT_OPEN_ROUND", decision.mode());
        assertEquals("EQUAL_CAPACITY_CONFIGURATION_ROUND", decision.reason());
    }

    @Test
    void defersACapacityChange() {
        var decision = policy.decide(round(CapacitySource.CONFIGURATION, 12), 11, now);

        assertFalse(decision.immediate());
        assertEquals("CAPACITY_CHANGED", decision.reason());
    }

    @Test
    void defersAfterCutoff() {
        var round = round(CapacitySource.CONFIGURATION, 12);
        round.cutoffAt = now;

        assertEquals("CUTOFF_REACHED", policy.decide(round, 12, now).reason());
    }

    @Test
    void defersARevisionOfTheActiveFloorPlan() {
        var round = round(CapacitySource.FLOOR_PLAN, 12);
        round.floorPlanVersion = new FloorPlanVersionEntity();

        assertEquals("ROUND_ALREADY_USES_FLOOR_PLAN", policy.decide(round, 12, now).reason());
    }

    @Test
    void defersWhenTheObservedRoundLostTheProcessingRace() {
        var round = round(CapacitySource.CONFIGURATION, 12);
        round.status = RoundStatus.COMPLETED;

        assertEquals("NO_ELIGIBLE_OPEN_ROUND", policy.decide(round, 12, now).reason());
    }

    private BiddingRoundEntity round(CapacitySource source, int capacity) {
        var round = new BiddingRoundEntity();
        round.status = RoundStatus.OPEN;
        round.capacitySource = source;
        round.seatCapacity = capacity;
        round.cutoffAt = now.plusSeconds(3600);
        return round;
    }
}