package de.gigaworks.seatbidding.resource.impl;

import de.gigaworks.seatbidding.bidding.BiddingService;
import de.gigaworks.seatbidding.dto.BiddingContextResponse;
import de.gigaworks.seatbidding.dto.ReplaceBidsRequest;
import de.gigaworks.seatbidding.resource.BiddingResource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BiddingResourceImpl implements BiddingResource {
    
    @Inject
    BiddingService service;
    
    @Override
    public BiddingContextResponse get() {
        return service.current();
    }
    
    @Override
    public BiddingContextResponse replace(ReplaceBidsRequest request) {
        return service.replace(request);
    }
    
}

