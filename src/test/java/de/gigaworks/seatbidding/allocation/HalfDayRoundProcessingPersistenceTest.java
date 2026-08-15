package de.gigaworks.seatbidding.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.gigaworks.seatbidding.persistence.AllocationUnitRepository;
import de.gigaworks.seatbidding.persistence.AllocationUnitType;
import de.gigaworks.seatbidding.persistence.AttendancePeriod;
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
import de.gigaworks.seatbidding.persistence.TokenLedgerRepository;
import de.gigaworks.seatbidding.round.RoundFactory;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = de.gigaworks.seatbidding.support.PostgresTestResource.class,
        restrictToAnnotatedClass = true)
class HalfDayRoundProcessingPersistenceTest {

    @Inject BiddingRoundRepository rounds;
    @Inject RoundDateRepository dates;
    @Inject EmployeeRepository employees;
    @Inject RoundParticipationRepository participations;
    @Inject BidRepository bids;
    @Inject AllocationUnitRepository units;
    @Inject SeatAssignmentRepository assignments;
    @Inject TokenLedgerRepository ledger;
    @Inject RoundAllocationAuditRepository audits;
    @Inject RoundFactory roundFactory;
    @Inject RoundProcessingService processing;

    @Test
    void winningPairPersistsOneUnitTwoMemberResultsAndSeparateDebits() {
        QuarkusTransaction.requiringNew().run(() -> {
            var round = rounds.findOpen().orElseThrow();
            round.seatCapacity = 1;
            round.biddingOpensAt = Instant.now().minusSeconds(604800);
            round.cutoffAt = Instant.now().minusSeconds(1);
            var day = dates.findForRound(round.id).getFirst();
            createBid(round, day, "morning@example.com", 20, AttendancePeriod.MORNING_ONLY);
            createBid(round, day, "afternoon@example.com", 10, AttendancePeriod.AFTERNOON_ONLY);
            createBid(round, day, "full@example.com", 25, AttendancePeriod.FULL_DAY);
        });

        assertTrue(processing.processDueRound());
        QuarkusTransaction.requiringNew().run(() -> {
            var round = rounds.findLatestCompleted().orElseThrow();
            var day = dates.findForRound(round.id).getFirst();
            assertEquals(2, units.count("roundDate.id", day.id));
            var pair = units.find("roundDate.id = ?1 and unitType = ?2", day.id,
                    AllocationUnitType.HALF_DAY_PAIR).singleResult();
            assertTrue(pair.assigned);
            assertEquals(30, pair.scoreTokens);
            var memberResults = assignments.findForDate(day.id).stream()
                    .filter(result -> result.allocationUnit.id.equals(pair.id)).toList();
            assertEquals(2, memberResults.size());
            assertEquals(List.of(AttendancePeriod.MORNING_ONLY, AttendancePeriod.AFTERNOON_ONLY),
                    memberResults.stream().map(result -> result.attendancePeriod).toList());
            assertEquals(2, ledger.count("round.id = ?1 and type = ?2", round.id, LedgerType.BID_SPEND));
            assertEquals(List.of(-20, -10), ledger.find("round.id = ?1 and type = ?2 order by amount",
                    round.id, LedgerType.BID_SPEND).stream().map(entry -> entry.amount).toList());
            var audit = audits.findForRound(round.id).orElseThrow();
            assertEquals("v4", audit.algorithmVersion);
            assertNotNull(audit.pairingAudit);
        });
    }

    private void createBid(de.gigaworks.seatbidding.persistence.BiddingRoundEntity round,
            de.gigaworks.seatbidding.persistence.RoundDateEntity day, String email, int tokens,
            AttendancePeriod period) {
        var employee = new EmployeeEntity();
        employee.email = email;
        employee.firstName = "Test";
        employee.lastName = "Employee";
        employee.enabled = true;
        employees.persistAndFlush(employee);
        var participation = roundFactory.createParticipation(round, employee, 0, Instant.now());
        participations.flush();
        var bid = new BidEntity();
        bid.roundDate = day;
        bid.participation = participation;
        bid.tokens = tokens;
        bid.attendancePeriod = period;
        bids.persist(bid);
    }

}