package de.gigaworks.seatbidding.floorplan;

import de.gigaworks.seatbidding.persistence.BiddingRoundEntity;
import de.gigaworks.seatbidding.persistence.CapacitySource;
import de.gigaworks.seatbidding.persistence.RoundStatus;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;

@ApplicationScoped
public class FloorPlanPublicationPolicy {

    public Decision decide(BiddingRoundEntity round, int draftSeatCapacity, Instant now) {
        if (round == null || round.status != RoundStatus.OPEN) {
            return Decision.deferred("NO_ELIGIBLE_OPEN_ROUND");
        }
        if (!now.isBefore(round.cutoffAt)) {
            return Decision.deferred("CUTOFF_REACHED");
        }
        if (round.capacitySource != CapacitySource.CONFIGURATION || round.floorPlanVersion != null) {
            return Decision.deferred("ROUND_ALREADY_USES_FLOOR_PLAN");
        }
        if (draftSeatCapacity != round.seatCapacity) {
            return Decision.deferred("CAPACITY_CHANGED");
        }
        return new Decision(true, "CURRENT_OPEN_ROUND", "EQUAL_CAPACITY_CONFIGURATION_ROUND");
    }

    public record Decision(boolean immediate, String mode, String reason) {
        private static Decision deferred(String reason) {
            return new Decision(false, "NEXT_SUCCESSOR_ROUND", reason);
        }
    }
}