package de.gigaworks.seatbidding.bidding;

import de.gigaworks.seatbidding.auth.EmployeeIdentityService;
import de.gigaworks.seatbidding.bootstrap.ParticipationReconciliationService;
import de.gigaworks.seatbidding.dto.BiddingContextResponse;
import de.gigaworks.seatbidding.dto.ReplaceBidsRequest;
import de.gigaworks.seatbidding.exception.ApplicationProblem;
import de.gigaworks.seatbidding.persistence.BidEntity;
import de.gigaworks.seatbidding.persistence.BidRepository;
import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.persistence.RoundDateRepository;
import de.gigaworks.seatbidding.persistence.RoundParticipationRepository;
import de.gigaworks.seatbidding.persistence.RoundStatus;
import de.gigaworks.seatbidding.round.SeatBiddingConfiguration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class BiddingService {
    
    @Inject
    EmployeeIdentityService identity;
    
    @Inject
    ParticipationReconciliationService reconciliation;
    
    @Inject
    BiddingRoundRepository rounds;
    
    @Inject
    RoundParticipationRepository participations;
    
    @Inject
    RoundDateRepository dates;
    
    @Inject
    BidRepository bids;
    
    @Inject
    SeatBiddingConfiguration configuration;
    
    @Inject
    Clock clock;
    
    @Transactional
    public BiddingContextResponse current() {
        var employee = identity.resolve();
        var participation = reconciliation.forOpenRound(employee);
        return response(participation, clock.instant());
    }
    
    @Transactional
    public BiddingContextResponse replace(ReplaceBidsRequest request) {
        var employee = identity.resolve();
        var openRound = rounds.findOpen().orElseThrow(() ->
                ApplicationProblem.conflict("ROUND_NOT_OPEN", "Bidding is unavailable", "No round currently accepts bids."));
        if (!openRound.id.equals(request.roundId())) {
            throw ApplicationProblem.conflict("STALE_ROUND", "The bidding round changed", "Refresh before saving this draft.");
        }
        var participation = participations.findForUpdate(openRound.id, employee.id, configuration.lockTimeout())
                .orElseGet(() -> reconciliation.forOpenRound(employee));
        var authoritativeRound = rounds.findById(openRound.id);
        var now = clock.instant();
        if (authoritativeRound.status != RoundStatus.OPEN) {
            throw ApplicationProblem.conflict("ROUND_PROCESSING", "Bidding has closed", "This round is being processed.");
        }
        if (!now.isBefore(authoritativeRound.cutoffAt)) {
            throw ApplicationProblem.conflict("CUTOFF_PASSED", "The cutoff has passed", "Bids cannot be changed at or after cutoff.");
        }
        
        var roundDates = dates.findForRound(authoritativeRound.id);
        var byDate = roundDates.stream().collect(Collectors.toMap(d -> d.targetDate, Function.identity()));
        Map<java.time.LocalDate, Integer> normalized;
        try {
            normalized = BidSetValidator.validateAndNormalize(
                    request.bids().stream().map(b -> new BidSetValidator.SubmittedBid(b.date(), b.tokens())).toList(),
                    byDate.keySet(), participation.startingBalance);
        }
        catch (BidValidationException invalid) {
            throw ApplicationProblem.badRequest(invalid.code(), "Invalid bid set", invalid.getMessage());
        }
        
        bids.delete("participation.id", participation.id);
        normalized.forEach((date, tokens) -> {
            var bid = new BidEntity();
            bid.participation = participation;
            bid.roundDate = byDate.get(date);
            bid.tokens = tokens;
            bids.persist(bid);
        });
        bids.flush();
        return response(participation, now);
    }
    
    private BiddingContextResponse response(de.gigaworks.seatbidding.persistence.RoundParticipationEntity participation,
            java.time.Instant now) {
        var roundDates = dates.findForRound(participation.round.id);
        var saved = bids.findForParticipation(participation.id).stream()
                .collect(Collectors.toMap(b -> b.roundDate.id, b -> b.tokens));
        int total = saved.values().stream().mapToInt(Integer::intValue).sum();
        var dayResponses = roundDates.stream().map(date -> new BiddingContextResponse.DayBid(
                date.targetDate, date.targetDate.getDayOfWeek(), saved.getOrDefault(date.id, 0))).toList();
        return new BiddingContextResponse(participation.round.id, participation.round.status,
                participation.round.cutoffAt, participation.round.scheduleZone, now,
                participation.startingBalance, total, participation.startingBalance - total, dayResponses);
    }
    
}
