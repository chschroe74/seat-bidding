package de.gigaworks.seatbidding.auth;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TrustedAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FormCookieIdentityProvider implements IdentityProvider<TrustedAuthenticationRequest> {
    
    @Override
    public Class<TrustedAuthenticationRequest> getRequestType() {
        return TrustedAuthenticationRequest.class;
    }
    
    @Override
    public Uni<SecurityIdentity> authenticate(TrustedAuthenticationRequest request,
            AuthenticationRequestContext context) {
        String email = EmailNormalizer.normalize(request.getPrincipal());
        if (!EmailNormalizer.isValid(email)) {
            return Uni.createFrom().nullItem();
        }
        return Uni.createFrom().item(QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(email))
                .addRole("employee")
                .build());
    }
    
}
