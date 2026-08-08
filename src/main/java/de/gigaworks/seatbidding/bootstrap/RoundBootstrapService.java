package de.gigaworks.seatbidding.bootstrap;

import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.round.RoundFactory;
import io.quarkus.runtime.StartupEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.Map;

@ApplicationScoped
public class RoundBootstrapService {
    
    @Inject
    BiddingRoundRepository rounds;
    
    @Inject
    RoundFactory roundFactory;
    
    @Inject
    Clock clock;
    
    @Transactional
    void onStart(@Observes StartupEvent ignored) {
        if (rounds.count() == 0) {
            var now = clock.instant();
            roundFactory.create(1, now, now, null, Map.of());
        }
    }
    
}

