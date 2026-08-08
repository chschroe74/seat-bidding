package de.gigaworks.seatbidding.resource.impl;

import de.gigaworks.seatbidding.dto.PublicConfigurationResponse;
import de.gigaworks.seatbidding.resource.PublicConfigurationResource;
import de.gigaworks.seatbidding.round.SeatBiddingConfiguration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PublicConfigurationResourceImpl implements PublicConfigurationResource {
    
    @Inject
    SeatBiddingConfiguration configuration;
    
    @Override
    public PublicConfigurationResponse get() {
        var client = configuration.publicClient();
        return new PublicConfigurationResponse(
                client.androidDownloadUrl().filter(s -> !s.isBlank()).orElse(null), "/api");
    }
    
}
