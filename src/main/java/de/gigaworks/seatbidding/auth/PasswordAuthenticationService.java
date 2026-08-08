package de.gigaworks.seatbidding.auth;

import de.gigaworks.seatbidding.exception.ApplicationProblem;
import de.gigaworks.seatbidding.persistence.AccountActivationRepository;
import de.gigaworks.seatbidding.persistence.EmployeeRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;

@ApplicationScoped
public class PasswordAuthenticationService {
    
    @Inject
    EmployeeRepository employees;
    
    @Inject
    AccountActivationRepository activations;
    
    @Inject
    PasswordHasher passwordHasher;
    
    @Inject
    PasswordPolicy passwordPolicy;
    
    @Inject
    Clock clock;
    
    @Transactional
    public void createPassword(String activationToken, String password, String confirmation) {
        passwordPolicy.validate(password, confirmation);
        String tokenHash = SecretDigests.sha256(activationToken);
        var candidate = activations.findByTokenHash(tokenHash).orElseThrow(this::invalidActivation);
        var employee = employees.findByIdForUpdate(candidate.employee.id)
                .filter(account -> account.enabled && account.passwordHash == null)
                .orElseThrow(this::invalidActivation);
        var activation = activations.findByTokenHashForUpdate(tokenHash)
                .filter(current -> current.employee.id.equals(employee.id))
                .orElseThrow(this::invalidActivation);
        var now = clock.instant();
        if (activation.activationTokenExpiresAt == null || !activation.activationTokenExpiresAt.isAfter(now)) {
            throw invalidActivation();
        }
        employee.passwordHash = passwordHasher.hash(password);
        employee.passwordSetAt = now;
        activations.delete(activation);
    }
    
    private ApplicationProblem invalidActivation() {
        return ApplicationProblem.badRequest("ACTIVATION_AUTHORIZATION_INVALID", "Activation authorization is invalid",
                "The activation authorization is invalid, expired, or already used.");
    }
    
}
