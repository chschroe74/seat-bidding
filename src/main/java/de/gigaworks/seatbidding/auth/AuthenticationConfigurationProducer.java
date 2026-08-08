package de.gigaworks.seatbidding.auth;

import de.gigaworks.seatbidding.round.SeatBiddingConfiguration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuthenticationConfigurationProducer {
    
    @Inject
    SeatBiddingConfiguration configuration;
    
    @Produces
    @ApplicationScoped
    AuthenticationConfiguration authenticationConfiguration() {
        return configuration.authentication();
    }
    
}
