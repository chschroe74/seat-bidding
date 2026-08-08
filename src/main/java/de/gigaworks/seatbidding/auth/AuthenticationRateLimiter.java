package de.gigaworks.seatbidding.auth;

import de.gigaworks.seatbidding.exception.ApplicationProblem;
import io.vertx.ext.web.RoutingContext;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class AuthenticationRateLimiter {
    
    private static final String EMAIL = "email";
    private static final String SOURCE = "source";

    private final Map<String, ArrayDeque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Map<String, FailureState> failures = new ConcurrentHashMap<>();

    @Inject
    Clock clock;
    
    @Inject
    AuthenticationConfiguration configuration;
    
    @Inject
    RoutingContext routingContext;
    
    public void checkStart(String email) {
        check("start", email, configuration.rateLimit().startPerHour(), Duration.ofHours(1));
    }
    
    public void checkResend(String email) {
        check("resend", email, configuration.rateLimit().resendPerHour(), Duration.ofHours(1));
    }
    
    public void checkVerify(String email) {
        check("verify", email, configuration.rateLimit().verifyPerFifteenMinutes(), Duration.ofMinutes(15));
    }
    
    public void checkLogin(String email) {
        checkLogin(email, source());
    }
    
    public void checkLogin(String email, String source) {
        check("login", email, source, configuration.rateLimit().loginPerFifteenMinutes(), Duration.ofMinutes(15));
        Instant now = clock.instant();
        long retry = Math.max(retryAfter(failureKey(EMAIL, email), now), retryAfter(failureKey(SOURCE, source), now));
        if (retry > 0) {
            throw ApplicationProblem.tooManyRequests(retry);
        }
    }
    
    public void loginFailed(String email) {
        loginFailed(email, source());
    }
    
    public void loginFailed(String email, String source) {
        recordFailure(failureKey(EMAIL, email));
        recordFailure(failureKey(SOURCE, source));
    }
    
    public void loginSucceeded(String email) {
        loginSucceeded(email, source());
    }
    
    public void loginSucceeded(String email, String source) {
        failures.remove(failureKey(EMAIL, email));
        failures.remove(failureKey(SOURCE, source));
    }
    
    private void check(String action, String email, int limit, Duration window) {
        check(action, email, source(), limit, window);
    }
    
    private void check(String action, String email, String source, int limit, Duration window) {
        checkKey(action + ":email:" + digest(email), limit, window);
        checkKey(action + ":source:" + digest(source), configuration.rateLimit().sourcePerMinute(), Duration.ofMinutes(1));
    }
    
    private void checkKey(String key, int limit, Duration window) {
        Instant now = clock.instant();
        var queue = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            Instant cutoff = now.minus(window);
            while (!queue.isEmpty() && !queue.peekFirst().isAfter(cutoff)) {
                queue.removeFirst();
            }
            if (queue.size() >= limit) {
                throw ApplicationProblem.tooManyRequests(Duration.between(now, queue.peekFirst().plus(window)).toSeconds());
            }
            queue.addLast(now);
        }
    }
    
    private void recordFailure(String key) {
        Instant now = clock.instant();
        failures.compute(key, (ignored, current) -> {
            int count = current == null || current.lastFailure.isBefore(now.minus(Duration.ofMinutes(15))) ? 1 : current.count + 1;
            long penalty = count < 3 ? 0 : Math.min(60, 1L << Math.min(6, count - 3));
            return new FailureState(count, now, now.plusSeconds(penalty));
        });
    }
    
    private long retryAfter(String key, Instant now) {
        FailureState state = failures.get(key);
        return state == null || !state.blockedUntil.isAfter(now) ? 0 : Duration.between(now, state.blockedUntil).toSeconds() + 1;
    }
    
    private String failureKey(String kind, String value) {
        return "failure:" + kind + ":" + digest(value);
    }
    
    private String digest(String value) {
        return SecretDigests.sha256(value == null ? "unknown" : value);
    }
    
    private String source() {
        var address = routingContext.request().remoteAddress();
        return address == null ? "unknown" : address.hostAddress();
    }
    
    private record FailureState(
            int count,
            Instant lastFailure,
            Instant blockedUntil) {
        
    }
    
}
