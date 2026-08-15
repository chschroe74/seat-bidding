package de.gigaworks.seatbidding.notification;

import de.gigaworks.seatbidding.auth.EmployeeIdentityService;
import de.gigaworks.seatbidding.exception.ApplicationProblem;
import de.gigaworks.seatbidding.persistence.BidReminderSuppressionRepository;
import de.gigaworks.seatbidding.persistence.BidRepository;
import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.persistence.RoundStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Clock;

@ApplicationScoped
public class ReminderSuppressionService {

    @Inject
    EmployeeIdentityService identity;

    @Inject
    BiddingRoundRepository rounds;

    @Inject
    BidReminderSuppressionRepository suppressions;

    @Inject
    BidRepository bids;

    @Inject
    EntityManager entityManager;

    @Inject
    Clock clock;

    @Transactional
    public void suppress(long roundId) {
        var employee = identity.resolve();
        var round = rounds.findByIdForUpdate(roundId)
                .orElseThrow(ReminderSuppressionService::inapplicable);
        if (round.status != RoundStatus.OPEN || !round.id.equals(rounds.findOpen().map(value -> value.id).orElse(null))
                || !clock.instant().isBefore(round.cutoffAt)) {
            throw inapplicable();
        }
        if (suppressions.exists(round.id, employee.id)) {
            return;
        }
        if (bids.hasPositiveBid(round.id, employee.id)) {
            throw ApplicationProblem.conflict("REMINDER_ALREADY_SATISFIED", "Reminder suppression unavailable",
                    "A positive bid has already been saved for the current round.");
        }
        entityManager.createNativeQuery("""
                INSERT INTO bid_reminder_suppression(round_id, employee_id, created_at)
                VALUES (?1, ?2, ?3)
                ON CONFLICT (round_id, employee_id) DO NOTHING
                """).setParameter(1, round.id).setParameter(2, employee.id).setParameter(3, clock.instant())
                .executeUpdate();
    }

    private static ApplicationProblem inapplicable() {
        return ApplicationProblem.conflict("REMINDER_SUPPRESSION_INAPPLICABLE", "Reminder suppression unavailable",
                "The requested bidding round is no longer open for reminder suppression.");
    }

}