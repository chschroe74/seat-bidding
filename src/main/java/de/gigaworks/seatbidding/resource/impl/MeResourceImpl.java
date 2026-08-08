package de.gigaworks.seatbidding.resource.impl;

import de.gigaworks.seatbidding.auth.CurrentEmployeeService;
import de.gigaworks.seatbidding.dto.MeResponse;
import de.gigaworks.seatbidding.resource.MeResource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MeResourceImpl implements MeResource {
    
    @Inject
    CurrentEmployeeService service;
    
    @Override
    public MeResponse get() {
        return service.current();
    }
    
}

