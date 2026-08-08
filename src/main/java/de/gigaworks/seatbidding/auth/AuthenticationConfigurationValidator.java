package de.gigaworks.seatbidding.auth;

import de.gigaworks.seatbidding.exception.ConfigurationException;
import io.quarkus.runtime.StartupEvent;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import java.net.URI;
import java.time.Duration;

import org.eclipse.microprofile.config.Config;

@ApplicationScoped
public class AuthenticationConfigurationValidator {
    
    @Inject
    AuthenticationConfiguration authentication;
    
    @Inject
    Config config;
    
    void validate(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE) StartupEvent ignored) {
        positive("activation.code-ttl", authentication.activation().codeTtl());
        positive("activation.resend-cooldown", authentication.activation().resendCooldown());
        positive("activation.token-ttl", authentication.activation().tokenTtl());
        secret("activation.code-pepper", authentication.activation().codePepper());
        
        if (authentication.password().minimumLength() < 15
                || authentication.password().maximumLength() < 128
                || authentication.password().minimumLength() > authentication.password().maximumLength()) {
            fail("password lengths must allow at least 15 to 128 Unicode code points");
        }
        if (authentication.password().argon2MemoryKib() < 1024
                || authentication.password().argon2Iterations() < 1
                || authentication.password().argon2Parallelism() < 1) {
            fail("Argon2id memory, iteration, and parallelism values must be positive and memory must be at least 1024 KiB");
        }
        
        if (authentication.activation().maximumAttempts() < 1
                || authentication.rateLimit().startPerHour() < 1
                || authentication.rateLimit().resendPerHour() < 1
                || authentication.rateLimit().verifyPerFifteenMinutes() < 1
                || authentication.rateLimit().loginPerFifteenMinutes() < 1
                || authentication.rateLimit().sourcePerMinute() < 1) {
            fail("authentication attempt and rate limits must be at least one");
        }
        authentication.allowedWebOrigins().forEach(this::origin);
        
        secret("quarkus.http.auth.session.encryption-key",
                config.getOptionalValue("quarkus.http.auth.session.encryption-key", String.class).orElse(null));
        secret("quarkus.rest-csrf.token-signature-key",
                config.getOptionalValue("quarkus.rest-csrf.token-signature-key", String.class).orElse(null));
        
        nonBlank("quarkus.mailer.from");
        nonBlank("quarkus.mailer.host");
        nonBlank("quarkus.mailer.username");
        nonBlank("quarkus.mailer.password");
        int port = config.getValue("quarkus.mailer.port", Integer.class);
        if (port < 1 || port > 65535) {
            fail("quarkus.mailer.port must be between 1 and 65535");
        }
    }
    
    private void origin(String value) {
        try {
            URI uri = URI.create(value);
            String expected = uri.getScheme() + "://" + uri.getRawAuthority();
            boolean localHttp = "http".equals(uri.getScheme())
                    && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
            if (uri.getRawAuthority() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getRawQuery() != null || !uri.getPath().isEmpty() || !value.equals(expected)
                    || !("https".equals(uri.getScheme()) || localHttp)) {
                fail("allowed-web-origins entries must be exact HTTPS origins (HTTP is allowed only for localhost)");
            }
        }
        catch (IllegalArgumentException _) {
            fail("allowed-web-origins contains an invalid origin");
        }
    }
    
    private void positive(String name, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            fail(name + " must be positive");
        }
    }
    
    private void secret(String name, String value) {
        if (value == null || value.length() < 32) {
            fail(name + " must contain at least 32 characters");
        }
    }
    
    private void nonBlank(String name) {
        if (config.getOptionalValue(name, String.class).map(String::isBlank).orElse(true)) {
            fail(name + " must be configured");
        }
    }
    
    private void fail(String message) {
        throw new ConfigurationException("Invalid authentication configuration: {}", message);
    }
    
}
