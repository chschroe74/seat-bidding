package de.gigaworks.seatbidding.auth;

import io.quarkus.vertx.web.RouteFilter;
import io.vertx.ext.web.RoutingContext;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;

@ApplicationScoped
public class LoginOriginSecurityFilter {
    
    @Inject
    AuthenticationConfiguration configuration;
    
    @RouteFilter(Integer.MAX_VALUE)
    void validateFormLoginOrigin(RoutingContext context) {
        if (!"POST".equals(context.request().method().name())
                || !"/j_security_check".equals(context.normalizedPath())) {
            context.next();
            return;
        }
        String origin = context.request().getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            origin = originOf(context.request().getHeader("Referer"));
        }
        if (!configuration.allowedWebOrigins().contains(origin)) {
            context.response().setStatusCode(403).putHeader("Cache-Control", "no-store").end();
            return;
        }
        context.next();
    }
    
    private String originOf(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            return uri.getScheme() == null || uri.getRawAuthority() == null
                    ? null : uri.getScheme() + "://" + uri.getRawAuthority();
        }
        catch (IllegalArgumentException _) {
            return null;
        }
    }
    
}
