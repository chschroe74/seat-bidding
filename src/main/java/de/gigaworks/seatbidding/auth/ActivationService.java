package de.gigaworks.seatbidding.auth;

import de.gigaworks.seatbidding.dto.AuthenticationStartResponse;
import de.gigaworks.seatbidding.dto.ActivationVerifyResponse;
import de.gigaworks.seatbidding.exception.ApplicationProblem;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ActivationService {
    
    @Inject
    ActivationStateService state;
    
    @Inject
    ActivationMailSender mailer;
    
    @Inject
    AuthenticationRateLimiter rateLimiter;
    
    public AuthenticationStartResponse start(String rawEmail) {
        String email = requiredEmail(rawEmail);
        rateLimiter.checkStart(email);
        var result = state.start(email);
        deliver(result);
        return new AuthenticationStartResponse(result.nextStep(), result.codeExpiresAt(), result.resendAvailableAt());
    }
    
    public java.time.Instant resend(String rawEmail) {
        String email = requiredEmail(rawEmail);
        rateLimiter.checkResend(email);
        var result = state.resend(email);
        deliver(result);
        return result.resendAvailableAt();
    }
    
    public ActivationVerifyResponse verify(String rawEmail, String code) {
        String email = requiredEmail(rawEmail);
        rateLimiter.checkVerify(email);
        var result = state.verify(email, code);
        return new ActivationVerifyResponse(result.token(), result.expiresAt());
    }
    
    private void deliver(ActivationStateService.StartResult result) {
        if (result.plaintextCode() == null) {
            return;
        }
        try {
            mailer.send(result.email(), result.plaintextCode(), result.codeExpiresAt());
        }
        catch (RuntimeException _) {
            state.deliveryFailed(result.employeeId());
            throw ApplicationProblem.serviceUnavailable("ACTIVATION_EMAIL_UNAVAILABLE",
                    "The activation email could not be sent. Try resending shortly.");
        }
    }
    
    private String requiredEmail(String value) {
        String email = EmailNormalizer.normalize(value);
        if (!EmailNormalizer.isValid(email)) {
            throw ApplicationProblem.badRequest("INVALID_EMAIL", "Invalid email", "Enter a valid email address.");
        }
        return email;
    }
    
}
