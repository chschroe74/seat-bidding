package de.gigaworks.seatbidding.resource.impl;

import de.gigaworks.seatbidding.auth.ActivationService;
import de.gigaworks.seatbidding.auth.PasswordAuthenticationService;
import de.gigaworks.seatbidding.dto.ActivationResendRequest;
import de.gigaworks.seatbidding.dto.ActivationVerifyRequest;
import de.gigaworks.seatbidding.dto.ActivationVerifyResponse;
import de.gigaworks.seatbidding.dto.AuthenticationStartRequest;
import de.gigaworks.seatbidding.dto.AuthenticationStartResponse;
import de.gigaworks.seatbidding.dto.CreatePasswordRequest;
import de.gigaworks.seatbidding.resource.AuthenticationResource;
import io.quarkus.csrf.reactive.runtime.CsrfTokenParameterProvider;
import io.quarkus.security.identity.SecurityIdentity;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class AuthenticationResourceImpl implements AuthenticationResource {
    
    public static final String CACHE_CONTROL = "Cache-Control";
    public static final String NO_STORE = "no-store";

    @Inject
    ActivationService activation;
    
    @Inject
    PasswordAuthenticationService passwords;
    
    @Inject
    SecurityIdentity identity;
    
    @Inject
    CsrfTokenParameterProvider csrfTokens;
    
    @Inject
    java.time.Clock clock;
    
    @Override
    public AuthenticationStartResponse start(AuthenticationStartRequest request) {
        return activation.start(request.email());
    }
    
    @Override
    public Response resend(ActivationResendRequest request) {
        var availableAt = activation.resend(request.email());
        long retryAfter = Math.max(1, java.time.Duration.between(clock.instant(), availableAt).toSeconds() + 1);
        return Response.accepted().header("Retry-After", retryAfter).header(CACHE_CONTROL, NO_STORE).build();
    }
    
    @Override
    public ActivationVerifyResponse verify(ActivationVerifyRequest request) {
        return activation.verify(request.email(), request.code());
    }
    
    @Override
    public Response createPassword(CreatePasswordRequest request) {
        passwords.createPassword(request.activationToken(), request.password(), request.passwordConfirmation());
        return Response.noContent().header(CACHE_CONTROL, NO_STORE).build();
    }
    
    @Override
    public Response csrf() {
        return Response.noContent().header("X-CSRF-TOKEN", csrfTokens.getToken())
                .header(CACHE_CONTROL, NO_STORE).build();
    }
    
    @Override
    public Response logout(String ignored) {
        io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism.logout(identity);
        return Response.noContent().header(CACHE_CONTROL, NO_STORE).build();
    }
    
}
