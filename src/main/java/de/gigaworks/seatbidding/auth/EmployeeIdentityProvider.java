package de.gigaworks.seatbidding.auth;

import de.gigaworks.seatbidding.persistence.EmployeeRepository;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.smallrye.mutiny.Uni;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EmployeeIdentityProvider implements IdentityProvider<UsernamePasswordAuthenticationRequest> {
    
    @Inject
    EmployeeRepository employees;
    
    @Inject
    PasswordHasher passwordHasher;
    
    @Inject
    AuthenticationRateLimiter rateLimiter;
    
    @Inject
    AuthenticationConfiguration configuration;
    
    private String dummyHash;
    
    @PostConstruct
    void initializeDummyHash() {
        dummyHash = passwordHasher.hash("not-a-real-user-password-value");
    }
    
    @Override
    public Class<UsernamePasswordAuthenticationRequest> getRequestType() {
        return UsernamePasswordAuthenticationRequest.class;
    }
    
    @Override
    public Uni<SecurityIdentity> authenticate(UsernamePasswordAuthenticationRequest request,
            AuthenticationRequestContext context) {
        return context.runBlocking(() -> verify(request));
    }
    
    private SecurityIdentity verify(UsernamePasswordAuthenticationRequest request) {
        String email = EmailNormalizer.normalize(request.getUsername());
        var routingContext = HttpSecurityUtils.getRoutingContextAttribute(request);
        var address = routingContext == null ? null : routingContext.request().remoteAddress();
        String source = address == null ? "unknown" : address.hostAddress();
        rateLimiter.checkLogin(email, source);
        String password = new String(request.getPassword().getPassword());
        var authenticated = QuarkusTransaction.requiringNew().call(() -> {
            var employee = EmailNormalizer.isValid(email) ? employees.findByEmail(email).orElse(null) : null;
            String hash = employee == null || employee.passwordHash == null ? dummyHash : employee.passwordHash;
            boolean verified = password.codePoints().count() <= configuration.password().maximumLength()
                    && passwordHasher.verify(password, hash);
            if (verified && employee != null && employee.enabled && employee.passwordHash != null
                    && passwordHasher.needsRehash(employee.passwordHash)) {
                employee.passwordHash = passwordHasher.hash(password);
            }
            return verified && employee != null && employee.enabled && employee.passwordHash != null
                    ? new AuthenticatedEmployee(employee.admin) : null;
        });
        if (authenticated == null) {
            rateLimiter.loginFailed(email, source);
            throw new AuthenticationFailedException();
        }
        rateLimiter.loginSucceeded(email, source);
        var builder = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(email))
                .addRole("USER");
        if (authenticated.admin()) {
            builder.addRole("ADMIN");
        }
        return builder.build();
    }
    
    private record AuthenticatedEmployee(
            boolean admin) {
        
    }
    
}
