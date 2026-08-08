package de.gigaworks.seatbidding.auth;

import de.gigaworks.seatbidding.persistence.EmployeeEntity;
import de.gigaworks.seatbidding.persistence.EmployeeRepository;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import io.quarkus.security.identity.SecurityIdentity;

@RequestScoped
public class EmployeeIdentityService {
    
    @Inject
    SecurityIdentity securityIdentity;
    
    @Inject
    EmployeeRepository employees;
    
    private EmployeeEntity resolved;
    
    public EmployeeEntity resolve() {
        if (resolved != null) {
            return resolved;
        }
        if (securityIdentity.isAnonymous()) {
            throw de.gigaworks.seatbidding.exception.ApplicationProblem
                    .unauthorized("SESSION_REQUIRED", "A valid application session is required.");
        }
        resolved = employees.findByEmail(EmailNormalizer.normalize(securityIdentity.getPrincipal().getName()))
                .filter(employee -> employee.enabled)
                .orElseThrow(() -> de.gigaworks.seatbidding.exception.ApplicationProblem
                        .unauthorized("SESSION_INVALID", "The session is no longer valid."));
        return resolved;
    }
    
}
