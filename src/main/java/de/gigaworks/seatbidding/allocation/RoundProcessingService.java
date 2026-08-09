package de.gigaworks.seatbidding.allocation;

import de.gigaworks.seatbidding.persistence.BidEntity;
import de.gigaworks.seatbidding.persistence.BidRepository;
import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.persistence.LedgerType;
import de.gigaworks.seatbidding.persistence.RoundDateRepository;
import de.gigaworks.seatbidding.persistence.RoundAllocationAuditEntity;
import de.gigaworks.seatbidding.persistence.RoundAllocationAuditRepository;
import de.gigaworks.seatbidding.persistence.RoundParticipationEntity;
import de.gigaworks.seatbidding.persistence.RoundParticipationRepository;
import de.gigaworks.seatbidding.persistence.RoundStatus;
import de.gigaworks.seatbidding.persistence.SeatAssignmentEntity;
import de.gigaworks.seatbidding.persistence.SeatAssignmentRepository;
import de.gigaworks.seatbidding.persistence.TokenLedgerEntity;
import de.gigaworks.seatbidding.persistence.TokenLedgerRepository;
import de.gigaworks.seatbidding.round.RoundFactory;
import de.gigaworks.seatbidding.tokens.BalanceCalculator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class RoundProcessingService {

    @Inject
    BiddingRoundRepository rounds;

    @Inject
    RoundDateRepository dates;

    @Inject
    RoundParticipationRepository participations;

    @Inject
    BidRepository bids;

    @Inject
    SeatAssignmentRepository assignments;

    @Inject
    RoundAllocationAuditRepository allocationAudits;

    @Inject
    TokenLedgerRepository ledger;

    @Inject
    AllocationEngine allocation;

    @Inject
    RoundFactory roundFactory;

    @Inject
    Clock clock;

    @Transactional
    public boolean processDueRound() {
        var now = clock.instant();
        var round = rounds.findDueForUpdate(now).orElse(null);
        if (round == null) {
            return false;
        }
        if (round.status != RoundStatus.OPEN || now.isBefore(round.cutoffAt)) {
            return false;
        }

        round.status = RoundStatus.PROCESSING;
        round.processingStartedAt = now;
        var roundDates = dates.findForRound(round.id);
        var roundBids = bids.findForRound(round.id);
        var datesById = roundDates.stream().collect(Collectors.toMap(date -> date.id, Function.identity()));
        var bidsById = roundBids.stream().collect(Collectors.toMap(bid -> bid.id, Function.identity()));
        var solution = allocation.allocate(round.id, round.seatCapacity,
                roundDates.stream().map(date -> new RoundAllocation.TargetDate(date.id, date.targetDate)).toList(),
                roundBids.stream().map(bid -> new RoundAllocation.Bid(bid.roundDate.id, bid.roundDate.targetDate,
                        bid.id, bid.participation.employee.id, bid.tokens)).toList());

        for (var result : solution.results()) {
            var assignment = new SeatAssignmentEntity();
            assignment.roundDate = datesById.get(result.dateId());
            assignment.bid = bidsById.get(result.bidId());
            assignment.assigned = result.assigned();
            assignment.tokenRank = result.tokenRank();
            assignment.finalRank = result.finalRank();
            assignment.resolution = result.resolution();
            assignment.boundaryTieGroup = result.boundaryTieGroup();
            assignment.tieGroup = null;
            assignment.drawValue = null;
            assignment.algorithmVersion = result.algorithmVersion();
            assignments.persist(assignment);
        }
        persistAudit(round, solution);
        assignments.flush();

        var spendByParticipation = new HashMap<Long, Integer>();
        for (var assignment : assignments.findAssignedForRound(round.id)) {
            BidEntity bid = assignment.bid;
            spendByParticipation.merge(bid.participation.id, bid.tokens, Integer::sum);
            addSpendLedger(bid, now);
        }

        Map<Long, Integer> carryByEmployee = new HashMap<>();
        for (RoundParticipationEntity participation : participations.findForRound(round.id)) {
            int spend = spendByParticipation.getOrDefault(participation.id, 0);
            var closing = BalanceCalculator.close(participation.startingBalance, spend, round.carryOverCap);
            participation.successfulBidTokens = spend;
            participation.remainingBalance = closing.remaining();
            participation.carriedOutTokens = closing.carriedOut();
            carryByEmployee.put(participation.employee.id, closing.carriedOut());
            if (closing.expired() > 0) {
                addExpiryLedger(participation, closing.expired(), now);
            }
        }

        round.status = RoundStatus.COMPLETED;
        round.processedAt = now;
        rounds.flush();
        roundFactory.create(round.sequenceNo + 1, now, round.cutoffAt, round, carryByEmployee);
        return true;
    }

    private void persistAudit(de.gigaworks.seatbidding.persistence.BiddingRoundEntity round,
            RoundAllocation.Solution solution) {
        var audit = new RoundAllocationAuditEntity();
        audit.round = round;
        audit.algorithmVersion = RoundAllocation.ALGORITHM_VERSION;
        audit.inputFingerprint = solution.inputFingerprint();
        audit.objectiveSummary = objectiveSummary(solution.objective());
        audit.selectedSolutionFingerprint = solution.selectedSolutionFingerprint();
        audit.randomSelectionValue = solution.randomSelectionValue();
        allocationAudits.persist(audit);
    }

    private static String objectiveSummary(RoundAllocation.Objective objective) {
        String vector = objective.sortedTieWinCounts().stream().map(String::valueOf).collect(Collectors.joining(","));
        return "{\"filledUnresolvedSlots\":" + objective.filledUnresolvedSlots()
                + ",\"distinctTieWinners\":" + objective.distinctTieWinners()
                + ",\"sortedTieWinCounts\":[" + vector + "]}";
    }

    private void addSpendLedger(BidEntity bid, java.time.Instant now) {
        var entry = new TokenLedgerEntity();
        entry.employee = bid.participation.employee;
        entry.round = bid.participation.round;
        entry.bid = bid;
        entry.type = LedgerType.BID_SPEND;
        entry.amount = -bid.tokens;
        entry.idempotencyKey = "bid:" + bid.id + ":spend";
        entry.occurredAt = now;
        ledger.persist(entry);
    }

    private void addExpiryLedger(RoundParticipationEntity participation, int expired, java.time.Instant now) {
        var entry = new TokenLedgerEntity();
        entry.employee = participation.employee;
        entry.round = participation.round;
        entry.type = LedgerType.EXPIRY;
        entry.amount = -expired;
        entry.idempotencyKey = "round:" + participation.round.id + ":employee:" + participation.employee.id + ":expiry";
        entry.occurredAt = now;
        ledger.persist(entry);
    }

}
