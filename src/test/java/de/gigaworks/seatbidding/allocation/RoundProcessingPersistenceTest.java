package de.gigaworks.seatbidding.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import de.gigaworks.seatbidding.persistence.BidEntity;
import de.gigaworks.seatbidding.persistence.BidRepository;
import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.persistence.EmployeeEntity;
import de.gigaworks.seatbidding.persistence.EmployeeRepository;
import de.gigaworks.seatbidding.persistence.LedgerType;
import de.gigaworks.seatbidding.persistence.RoundDateRepository;
import de.gigaworks.seatbidding.persistence.RoundParticipationEntity;
import de.gigaworks.seatbidding.persistence.RoundParticipationRepository;
import de.gigaworks.seatbidding.persistence.RoundStatus;
import de.gigaworks.seatbidding.persistence.SeatAssignmentRepository;
import de.gigaworks.seatbidding.persistence.TokenLedgerRepository;
import de.gigaworks.seatbidding.round.RoundFactory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = de.gigaworks.seatbidding.support.PostgresTestResource.class, restrictToAnnotatedClass = true)
class RoundProcessingPersistenceTest {
    @Inject BiddingRoundRepository rounds;
    @Inject EmployeeRepository employees;
    @Inject RoundDateRepository dates;
    @Inject RoundParticipationRepository participations;
    @Inject BidRepository bids;
    @Inject SeatAssignmentRepository assignments;
    @Inject TokenLedgerRepository ledger;
    @Inject RoundFactory roundFactory;
    @Inject RoundProcessingService processing;

    @Test
    void boundaryTieChargesOnlyWinnersAndCreatesOneSuccessor() {
        QuarkusTransaction.requiringNew().run(() -> {
            var round = rounds.findOpen().orElseThrow();
            round.seatCapacity = 2;
            var now = Instant.now();
            round.biddingOpensAt = now.minusSeconds(7 * 24 * 60 * 60);
            round.cutoffAt = now.minusSeconds(1);
            var day = dates.findForRound(round.id).getFirst();
            int[] amounts = {20, 10, 10};
            for (int i = 0; i < amounts.length; i++) {
                var employee = new EmployeeEntity();
                employee.email = "employee" + i + "@example.com";
                employee.firstName = "Employee";
                employee.lastName = Integer.toString(i);
                employee.enabled = true;
                employees.persist(employee);
                employees.flush();
                RoundParticipationEntity participation = roundFactory.createParticipation(round, employee, 0, Instant.now());
                participations.flush();
                var bid = new BidEntity();
                bid.roundDate = day;
                bid.participation = participation;
                bid.tokens = amounts[i];
                bids.persist(bid);
            }
        });

        assertTrue(processing.processDueRound());
        assertTrue(!processing.processDueRound());

        QuarkusTransaction.requiringNew().run(() -> {
            var completed = rounds.findLatestCompleted().orElseThrow();
            assertEquals(RoundStatus.COMPLETED, completed.status);
            assertNotNull(completed.processedAt);
            assertEquals(1, rounds.count("status", RoundStatus.OPEN));
            var day = dates.findForRound(completed.id).getFirst();
            var results = assignments.findForDate(day.id);
            assertEquals(3, results.size());
            assertEquals(2, results.stream().filter(a -> a.assigned).count());
            assertEquals(2, ledger.count("round.id = ?1 and type = ?2", completed.id, LedgerType.BID_SPEND));

            List<Integer> spends = new ArrayList<>();
            for (var participation : participations.findForRound(completed.id)) {
                spends.add(participation.successfulBidTokens);
                long ledgerBalance = ledger.find("round.id = ?1 and employee.id = ?2", completed.id, participation.employee.id)
                        .stream().mapToLong(entry -> entry.amount).sum();
                assertEquals(participation.carriedOutTokens.longValue(), ledgerBalance);
            }
            assertEquals(2, spends.stream().filter(value -> value > 0).count());
            assertEquals(20, spends.stream().mapToInt(Integer::intValue).max().orElseThrow());
        });
    }
}
