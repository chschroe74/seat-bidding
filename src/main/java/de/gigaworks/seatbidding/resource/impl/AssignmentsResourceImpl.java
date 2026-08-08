package de.gigaworks.seatbidding.resource.impl;

import de.gigaworks.seatbidding.allocation.AssignmentQueryService;
import de.gigaworks.seatbidding.dto.AssignmentsResponse;
import de.gigaworks.seatbidding.resource.AssignmentsResource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AssignmentsResourceImpl implements AssignmentsResource {
    
    @Inject
    AssignmentQueryService service;
    
    @Override
    public AssignmentsResponse get() {
        return service.latest();
    }
    
}

