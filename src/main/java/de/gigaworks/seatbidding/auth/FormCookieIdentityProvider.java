package de.gigaworks.seatbidding.auth;

import de.gigaworks.seatbidding.persistence.EmployeeRepository;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TrustedAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.quarkus.narayana.jta.QuarkusTransaction;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FormCookieIdentityProvider implements IdentityProvider<TrustedAuthenticationRequest> {
    
    @Inject
    EmployeeRepository employees;
    
    @Override
    public Class<TrustedAuthenticationRequest> getRequestType() {
        return TrustedAuthenticationRequest.class;
    }
    
    @Override
    public Uni<SecurityIdentity> authenticate(TrustedAuthenticationRequest request,
            AuthenticationRequestContext context) {
        String email = EmailNormalizer.normalize(request.getPrincipal());
        return context.runBlocking(() -> restore(email));
    }
    
    private SecurityIdentity restore(String email) {
        if (!EmailNormalizer.isValid(email)) {
            return null;
        }
        var employee = QuarkusTransaction.requiringNew().call(() ->
                employees.findByEmail(email).filter(value -> value.enabled)
                        .map(value -> new RestoredEmployee(value.admin)).orElse(null));
        if (employee == null) {
            return null;
        }
        var builder = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(email))
                .addRole("USER");
        if (employee.admin()) {
            builder.addRole("ADMIN");
        }
        return builder.build();
    }
    
    private record RestoredEmployee(
            boolean admin) {
        
    }
    
}
