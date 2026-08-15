package de.gigaworks.seatbidding.round;

import de.gigaworks.seatbidding.exception.ConfigurationException;
import de.gigaworks.seatbidding.persistence.BiddingRoundEntity;
import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.persistence.EmployeeRepository;
import de.gigaworks.seatbidding.persistence.LedgerType;
import de.gigaworks.seatbidding.persistence.RoundDateEntity;
import de.gigaworks.seatbidding.persistence.RoundDateRepository;
import de.gigaworks.seatbidding.persistence.RoundParticipationEntity;
import de.gigaworks.seatbidding.persistence.RoundParticipationRepository;
import de.gigaworks.seatbidding.persistence.RoundStatus;
import de.gigaworks.seatbidding.persistence.SeatReservationRepository;
import de.gigaworks.seatbidding.persistence.TokenLedgerEntity;
import de.gigaworks.seatbidding.persistence.TokenLedgerRepository;
import de.gigaworks.seatbidding.tokens.BalanceCalculator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;

@ApplicationScoped
public class RoundFactory {

    @Inject
    SeatBiddingConfiguration configuration;

    @Inject
    RoundSchedule schedule;

    @Inject
    BiddingRoundRepository rounds;

    @Inject
    RoundDateRepository dates;

    @Inject
    RoundParticipationRepository participations;

    @Inject
    EmployeeRepository employees;

    @Inject
    TokenLedgerRepository ledger;

    @Inject
    SeatReservationRepository reservations;

    public BiddingRoundEntity create(long sequenceNo, Instant opensAt, Instant cutoffSearchStart,
            BiddingRoundEntity predecessor, Map<Long, Integer> carryByEmployee) {
        var zone = configuration.timeZone();
        var cutoffAt = schedule.nextCutoff(configuration.scheduler().cron(), zone, cutoffSearchStart);
        var targetDates = schedule.targetDates(cutoffAt, zone);
        var existingReservations = reservations.findForDatesForUpdate(targetDates);
        if (existingReservations.stream().anyMatch(value -> value.reservedSeatCount > configuration.seatCapacity())) {
            throw new ConfigurationException("An existing seat reservation exceeds the configured physical capacity");
        }
        var round = new BiddingRoundEntity();
        round.status = RoundStatus.OPEN;
        round.sequenceNo = sequenceNo;
        round.biddingOpensAt = opensAt;
        round.cutoffAt = cutoffAt;
        round.scheduleZone = zone.getId();
        round.tokensGranted = configuration.tokensPerRound();
        round.carryOverCap = configuration.carryOverCap();
        round.seatCapacity = configuration.seatCapacity();
        round.predecessor = predecessor;
        rounds.persist(round);
        rounds.flush();

        for (int i = 0; i < targetDates.size(); i++) {
            var date = new RoundDateEntity();
            date.round = round;
            date.targetDate = targetDates.get(i);
            date.ordinal = (short) (i + 1);
            dates.persist(date);
        }

        for (var employee : employees.listAll()) {
            createParticipation(round, employee, carryByEmployee.getOrDefault(employee.id, 0), opensAt);
        }
        return round;
    }

    public RoundParticipationEntity createParticipation(BiddingRoundEntity round,
            de.gigaworks.seatbidding.persistence.EmployeeEntity employee,
            int carriedIn, Instant occurredAt) {
        var participation = new RoundParticipationEntity();
        participation.round = round;
        participation.employee = employee;
        participation.grantTokens = round.tokensGranted;
        participation.carriedInTokens = carriedIn;
        participation.startingBalance = BalanceCalculator.nextStartingBalance(round.tokensGranted, carriedIn);
        participation.successfulBidTokens = 0;
        participations.persist(participation);
        addLedger(employee, round, LedgerType.GRANT, round.tokensGranted,
                "round:" + round.id + ":employee:" + employee.id + ":grant", occurredAt);
        if (carriedIn > 0) {
            addLedger(employee, round, LedgerType.CARRY_IN, carriedIn,
                    "round:" + round.id + ":employee:" + employee.id + ":carry-in", occurredAt);
        }
        return participation;
    }

    private void addLedger(de.gigaworks.seatbidding.persistence.EmployeeEntity employee, BiddingRoundEntity round,
            LedgerType type, int amount, String key, Instant occurredAt) {
        if (amount == 0) {
            return;
        }
        var entry = new TokenLedgerEntity();
        entry.employee = employee;
        entry.round = round;
        entry.type = type;
        entry.amount = amount;
        entry.idempotencyKey = key;
        entry.occurredAt = occurredAt;
        ledger.persist(entry);
    }

}