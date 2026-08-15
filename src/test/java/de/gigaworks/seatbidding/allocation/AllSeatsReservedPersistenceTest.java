package de.gigaworks.seatbidding.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.gigaworks.seatbidding.persistence.BidEntity;
import de.gigaworks.seatbidding.persistence.BidRepository;
import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.persistence.EmployeeEntity;
import de.gigaworks.seatbidding.persistence.EmployeeRepository;
import de.gigaworks.seatbidding.persistence.LedgerType;
import de.gigaworks.seatbidding.persistence.RoundAllocationAuditRepository;
import de.gigaworks.seatbidding.persistence.RoundDateRepository;
import de.gigaworks.seatbidding.persistence.RoundParticipationRepository;
import de.gigaworks.seatbidding.persistence.SeatAssignmentRepository;
import de.gigaworks.seatbidding.persistence.SeatReservationEntity;
import de.gigaworks.seatbidding.persistence.SeatReservationRepository;
import de.gigaworks.seatbidding.persistence.TokenLedgerRepository;
import de.gigaworks.seatbidding.round.RoundFactory;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = de.gigaworks.seatbidding.support.PostgresTestResource.class,
        restrictToAnnotatedClass = true)
class AllSeatsReservedPersistenceTest {

    @Inject BiddingRoundRepository rounds;
    @Inject RoundDateRepository dates;
    @Inject EmployeeRepository employees;
    @Inject RoundParticipationRepository participations;
    @Inject BidRepository bids;
    @Inject SeatReservationRepository reservations;
    @Inject SeatAssignmentRepository assignments;
    @Inject RoundAllocationAuditRepository audits;
    @Inject TokenLedgerRepository ledger;
    @Inject RoundFactory roundFactory;
    @Inject RoundProcessingService processing;

    @Test
    void allSeatsReservedCreatesOnlyAnUnsuccessfulBidAndNoSpend() {
        QuarkusTransaction.requiringNew().run(() -> {
            var round = rounds.findOpen().orElseThrow();
            round.seatCapacity = 2;
            round.biddingOpensAt = Instant.now().minusSeconds(7 * 24 * 60 * 60);
            round.cutoffAt = Instant.now().minusSeconds(1);
            var day = dates.findForRound(round.id).getFirst();
            var employee = new EmployeeEntity();
            employee.email = "all-reserved@example.com";
            employee.firstName = "All";
            employee.lastName = "Reserved";
            employee.enabled = true;
            employees.persistAndFlush(employee);
            var participation = roundFactory.createParticipation(round, employee, 0, Instant.now());
            participations.flush();
            var bid = new BidEntity();
            bid.roundDate = day;
            bid.participation = participation;
            bid.tokens = 20;
            bids.persist(bid);
            var reservation = new SeatReservationEntity();
            reservation.targetDate = day.targetDate;
            reservation.reservedSeatCount = 2;
            reservation.description = "Private event";
            reservation.createdBy = employee;
            reservations.persist(reservation);
        });

        assertTrue(processing.processDueRound());
        QuarkusTransaction.requiringNew().run(() -> {
            var completed = rounds.findLatestCompleted().orElseThrow();
            var result = assignments.findForDate(dates.findForRound(completed.id).getFirst().id).getFirst();
            assertFalse(result.assigned);
            assertEquals(AllocationResolution.FIXED_LOSER, result.resolution);
            assertEquals(0, result.bid.participation.successfulBidTokens);
            assertEquals(0, ledger.count("round.id = ?1 and type = ?2", completed.id, LedgerType.BID_SPEND));
            assertEquals("v4", audits.findForRound(completed.id).orElseThrow().algorithmVersion);
            assertEquals(1, reservations.count("targetDate", result.roundDate.targetDate));
        });
    }

}