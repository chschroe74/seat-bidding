package de.gigaworks.seatbidding.round;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;

@ApplicationScoped
public class TimeProducer {
    
    @Produces
    @ApplicationScoped
    Clock clock() {
        return Clock.systemUTC();
    }
    
}

