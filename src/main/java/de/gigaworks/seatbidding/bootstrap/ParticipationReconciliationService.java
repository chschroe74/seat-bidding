package de.gigaworks.seatbidding.bootstrap;

import de.gigaworks.seatbidding.exception.ApplicationProblem;
import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.persistence.EmployeeEntity;
import de.gigaworks.seatbidding.persistence.RoundParticipationEntity;
import de.gigaworks.seatbidding.persistence.RoundParticipationRepository;
import de.gigaworks.seatbidding.round.RoundFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;

@ApplicationScoped
public class ParticipationReconciliationService {
    
    @Inject
    BiddingRoundRepository rounds;
    
    @Inject
    RoundParticipationRepository participations;
    
    @Inject
    RoundFactory roundFactory;
    
    @Inject
    Clock clock;
    
    public RoundParticipationEntity forOpenRound(EmployeeEntity employee) {
        var round = rounds.findOpen().orElseThrow(() -> ApplicationProblem.notFound("OPEN_ROUND_NOT_FOUND", "No open bidding round exists."));
        return participations.find("round.id = ?1 and employee.id = ?2", round.id, employee.id).firstResultOptional()
                .orElseGet(() -> roundFactory.createParticipation(round, employee, 0, clock.instant()));
    }
    
}

