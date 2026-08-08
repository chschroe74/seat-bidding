package de.gigaworks.seatbidding.auth;

import de.gigaworks.seatbidding.dto.MeResponse;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CurrentEmployeeService {
    
    @Inject
    EmployeeIdentityService identity;
    
    @Transactional
    public MeResponse current() {
        var employee = identity.resolve();
        return new MeResponse(employee.id, employee.firstName, employee.lastName, employee.email);
    }
    
}
