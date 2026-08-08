package de.gigaworks.seatbidding.auth;

import de.gigaworks.seatbidding.exception.ApplicationProblem;
import de.gigaworks.seatbidding.persistence.AccountActivationEntity;
import de.gigaworks.seatbidding.persistence.AccountActivationRepository;
import de.gigaworks.seatbidding.persistence.EmployeeRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;

@ApplicationScoped
public class ActivationStateService {
    
    @Inject
    EmployeeRepository employees;
    
    @Inject
    AccountActivationRepository activations;
    
    @Inject
    AuthenticationConfiguration configuration;
    
    @Inject
    SecureTokenGenerator tokens;
    
    @Inject
    Clock clock;
    
    @Transactional
    public StartResult start(String email) {
        var employee = employees.findByEmailForUpdate(email)
                .filter(candidate -> candidate.enabled)
                .orElseThrow(this::accountUnavailable);
        if (employee.passwordHash != null) {
            return StartResult.passwordRequired();
        }
        Instant now = clock.instant();
        var activation = activations.findByEmployeeForUpdate(employee.id).orElse(null);
        if (activation != null && activation.codeDigest != null && activation.codeExpiresAt.isAfter(now)) {
            return StartResult.codeRequired(null, employee.email, activation.codeExpiresAt,
                    activation.lastSentAt.plus(configuration.activation().resendCooldown()));
        }
        return issue(employee, activation, now);
    }
    
    @Transactional
    public StartResult resend(String email) {
        var employee = employees.findByEmailForUpdate(email)
                .filter(candidate -> candidate.enabled && candidate.passwordHash == null)
                .orElseThrow(this::accountUnavailable);
        Instant now = clock.instant();
        var activation = activations.findByEmployeeForUpdate(employee.id).orElse(null);
        if (activation != null && activation.lastSentAt != null) {
            Instant available = activation.lastSentAt.plus(configuration.activation().resendCooldown());
            if (available.isAfter(now)) {
                throw ApplicationProblem.tooManyRequests(java.time.Duration.between(now, available).toSeconds() + 1);
            }
        }
        return issue(employee, activation, now);
    }
    
    @Transactional
    public void deliveryFailed(Long employeeId) {
        activations.findByEmployeeForUpdate(employeeId).ifPresent(activation -> {
            activation.codeDigest = null;
            activation.codeExpiresAt = null;
            activation.failedAttempts = 0;
            activation.lastSentAt = null;
        });
    }
    
    @Transactional(dontRollbackOn = ApplicationProblem.class)
    public VerifiedResult verify(String email, String code) {
        var employee = employees.findByEmailForUpdate(email)
                .filter(candidate -> candidate.enabled && candidate.passwordHash == null)
                .orElseThrow(this::invalidCode);
        var activation = activations.findByEmployeeForUpdate(employee.id).orElseThrow(this::invalidCode);
        Instant now = clock.instant();
        if (activation.codeDigest == null || activation.codeExpiresAt == null || !activation.codeExpiresAt.isAfter(now)
                || activation.failedAttempts >= configuration.activation().maximumAttempts()) {
            invalidateCode(activation);
            throw invalidCode();
        }
        String supplied = SecretDigests.hmacSha256(configuration.activation().codePepper(), code);
        if (!SecretDigests.matches(activation.codeDigest, supplied)) {
            activation.failedAttempts++;
            if (activation.failedAttempts >= configuration.activation().maximumAttempts()) {
                invalidateCode(activation);
            }
            throw invalidCode();
        }
        String token = tokens.opaqueToken();
        Instant expiresAt = now.plus(configuration.activation().tokenTtl());
        activation.codeDigest = null;
        activation.codeExpiresAt = null;
        activation.activationTokenHash = SecretDigests.sha256(token);
        activation.activationTokenExpiresAt = expiresAt;
        activation.verifiedAt = now;
        return new VerifiedResult(token, expiresAt);
    }
    
    private StartResult issue(de.gigaworks.seatbidding.persistence.EmployeeEntity employee,
            AccountActivationEntity activation, Instant now) {
        if (activation == null) {
            activation = new AccountActivationEntity();
            activation.employee = employee;
            activations.persist(activation);
        }
        String code = tokens.activationCode();
        activation.codeDigest = SecretDigests.hmacSha256(configuration.activation().codePepper(), code);
        activation.codeExpiresAt = now.plus(configuration.activation().codeTtl());
        activation.failedAttempts = 0;
        activation.lastSentAt = now;
        activation.activationTokenHash = null;
        activation.activationTokenExpiresAt = null;
        activation.verifiedAt = null;
        return new StartResult("CODE_REQUIRED", code, employee.id, employee.email, activation.codeExpiresAt,
                now.plus(configuration.activation().resendCooldown()));
    }
    
    private void invalidateCode(AccountActivationEntity activation) {
        activation.codeDigest = null;
        activation.codeExpiresAt = null;
    }
    
    private ApplicationProblem accountUnavailable() {
        return ApplicationProblem.forbidden("ACCOUNT_UNAVAILABLE", "The account is unavailable.");
    }
    
    private ApplicationProblem invalidCode() {
        return ApplicationProblem.badRequest("ACTIVATION_INVALID", "Activation could not be verified",
                "The activation code is invalid, expired, or exhausted.");
    }
    
    public record StartResult(
            String nextStep,
            String plaintextCode,
            Long employeeId,
            String email,
            Instant codeExpiresAt,
            Instant resendAvailableAt) {
        
        static StartResult passwordRequired() {
            return new StartResult("PASSWORD_REQUIRED", null, null, null, null, null);
        }
        
        static StartResult codeRequired(String code, String email, Instant expires, Instant resend) {
            return new StartResult("CODE_REQUIRED", code, null, email, expires, resend);
        }
        
    }
    
    public record VerifiedResult(
            String token,
            Instant expiresAt) {
        
    }
    
}
