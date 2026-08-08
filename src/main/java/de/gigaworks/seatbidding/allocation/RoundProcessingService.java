package de.gigaworks.seatbidding.allocation;

import de.gigaworks.seatbidding.persistence.BidEntity;
import de.gigaworks.seatbidding.persistence.BidRepository;
import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.persistence.LedgerType;
import de.gigaworks.seatbidding.persistence.RoundDateRepository;
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
        var spendByParticipation = new HashMap<Long, Integer>();
        
        for (var date : dates.findForRound(round.id)) {
            var dateBids = bids.findForDate(date.id);
            var byId = dateBids.stream().collect(Collectors.toMap(b -> b.id, Function.identity()));
            var results = allocation.allocate(dateBids.stream()
                    .map(b -> new AllocationEngine.Bid(b.id, b.tokens)).toList(), round.seatCapacity);
            for (var result : results) {
                BidEntity bid = byId.get(result.bidId());
                var assignment = new SeatAssignmentEntity();
                assignment.roundDate = date;
                assignment.bid = bid;
                assignment.assigned = result.assigned();
                assignment.finalRank = result.finalRank();
                assignment.tieGroup = result.tieGroup();
                assignment.drawValue = result.drawValue();
                assignment.algorithmVersion = result.algorithmVersion();
                assignments.persist(assignment);
                if (result.assigned()) {
                    spendByParticipation.merge(bid.participation.id, bid.tokens, Integer::sum);
                    addSpendLedger(bid, now);
                }
            }
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
