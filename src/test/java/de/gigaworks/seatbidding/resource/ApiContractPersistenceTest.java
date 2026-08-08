package de.gigaworks.seatbidding.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.gigaworks.seatbidding.auth.PasswordHasher;
import de.gigaworks.seatbidding.persistence.AccountActivationRepository;
import de.gigaworks.seatbidding.persistence.EmployeeEntity;
import de.gigaworks.seatbidding.persistence.EmployeeRepository;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = de.gigaworks.seatbidding.support.PostgresTestResource.class,
        restrictToAnnotatedClass = true)
class ApiContractPersistenceTest {
    private static final String ORIGIN = "https://seat.test";
    private static final String PASSWORD = "a valid long password with spaces";
    @Inject EmployeeRepository employees;
    @Inject AccountActivationRepository activations;
    @Inject MockMailbox mailbox;
    @Inject PasswordHasher passwordHasher;

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
    }

    @Test
    void openApiAndPublicConfigurationDescribeFormCookie() {
        given().get("/api/public/configuration").then().statusCode(200)
                .header("Content-Security-Policy", containsString("script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval'"))
                .header("Content-Security-Policy", containsString("font-src 'self' data:"))
                .body("apiBasePath", equalTo("/api"));
        String document = given().get("/q/openapi").then().statusCode(200).extract().asString();
        assertTrue(document.contains("formCookie"));
        assertFalse(document.contains("androidSession"));
        assertFalse(document.toLowerCase().contains("bearerformat"));
    }

    @Test
    void activationPasswordCreationAutomaticFormLoginCookieAndLogout() {
        provision("alex@example.com", true, null);
        Csrf csrf = csrf();
        postJson("/api/auth/start", Map.of("email", " Alex@Example.com "), csrf).then().statusCode(200)
                .header("Cache-Control", equalTo("no-store")).body("nextStep", equalTo("CODE_REQUIRED"));
        String code = latestCode("alex@example.com");
        QuarkusTransaction.requiringNew().run(() -> {
            var activation = activations.findByEmployee(employees.findByEmail("alex@example.com").orElseThrow().id).orElseThrow();
            assertNotEquals(code, activation.codeDigest);
            assertFalse(activation.codeDigest.contains(code));
        });

        String token = postJson("/api/auth/activation/verify", Map.of("email", "alex@example.com", "code", code), csrf)
                .then().statusCode(200).body("activationToken", notNullValue()).extract().path("activationToken");
        postJson("/api/auth/activation/password", Map.of("activationToken", token, "password", PASSWORD,
                "passwordConfirmation", PASSWORD), csrf).then().statusCode(204).body(equalTo(""));

        Response login = login("alex@example.com", PASSWORD, ORIGIN, true);
        String setCookie = login.header("Set-Cookie");
        String normalizedCookie = setCookie.toLowerCase(java.util.Locale.ROOT);
        assertTrue(normalizedCookie.contains("seat_session=") && normalizedCookie.contains("httponly")
                && normalizedCookie.contains("samesite=strict") && normalizedCookie.contains("path=/")
                && normalizedCookie.contains("max-age=2592000") && normalizedCookie.contains("secure")
                && !normalizedCookie.contains("domain="), setCookie);
        String session = login.getDetailedCookie("seat_session").getValue();
        given().cookie("seat_session", session).get("/api/me").then().statusCode(200)
                .body("email", equalTo("alex@example.com"));

        postWithSession("/api/auth/logout", csrf, session).then().statusCode(204)
                .header("Set-Cookie", containsString("Max-Age=0"));
        given().cookie("seat_session", session).get("/api/me").then().statusCode(200);
        // The original copied stateless cookie remains usable by design; the response expires only this client's cookie.
    }

    @Test
    void passwordAuthenticationRejectsWrongUnknownAndInactiveEmployeesAndBadOrigins() {
        provision("known@example.com", true, PASSWORD);
        login("known@example.com", PASSWORD, ORIGIN, true).then().statusCode(200);
        Response noOrigin = login("known@example.com", PASSWORD, null, false);
        noOrigin.then().statusCode(403);
        assertEquals(null, noOrigin.header("Set-Cookie"));
        Response wrongOrigin = login("known@example.com", PASSWORD, "https://attacker.example", true);
        wrongOrigin.then().statusCode(403);
        assertEquals(null, wrongOrigin.header("Set-Cookie"));
        loginWithReferer("known@example.com", PASSWORD, ORIGIN + "/login").then().statusCode(200);
        login("known@example.com", "wrong password value", ORIGIN, true).then().statusCode(401);
        login("unknown@example.com", PASSWORD, ORIGIN, true).then().statusCode(401);
        provision("inactive@example.com", false, PASSWORD);
        login("inactive@example.com", PASSWORD, ORIGIN, true).then().statusCode(401);
    }

    @Test
    void csrfCookieIsSignedReadableAndRequiredForJsonChanges() {
        Response response = given().get("/api/auth/csrf").then().statusCode(204).extract().response();
        String setCookie = response.header("Set-Cookie");
        assertTrue(setCookie.contains("csrf-token=") && setCookie.contains("SameSite=Strict")
                && setCookie.contains("Secure")
                && !setCookie.contains("HttpOnly"));
        String cookie = response.getDetailedCookie("csrf-token").getValue();
        String token = response.header("X-CSRF-TOKEN");
        assertNotEquals(cookie, token);
        given().contentType(ContentType.JSON).body(Map.of("email", "missing@example.com"))
                .post("/api/auth/start").then().statusCode(400).body("code", equalTo("REQUEST_REJECTED"));
        given().cookie("csrf-token", cookie).header("X-CSRF-TOKEN", "invalid")
                .contentType(ContentType.JSON).body(Map.of("email", "invalid@example.com"))
                .post("/api/auth/start").then().statusCode(400).body("code", equalTo("REQUEST_REJECTED"));
        postJson("/api/auth/start", Map.of("email", "valid@example.com"), new Csrf(cookie, token))
                .then().statusCode(403).body("code", equalTo("ACCOUNT_UNAVAILABLE"));
    }

    @Test
    void activeFormCookieIsRenewedAfterConfiguredInterval() throws Exception {
        provision("renewal@example.com", true, PASSWORD);
        String session = login("renewal@example.com", PASSWORD, ORIGIN, true)
                .then().statusCode(200).extract().detailedCookie("seat_session").getValue();
        Thread.sleep(1_100);
        given().cookie("seat_session", session).get("/api/me").then().statusCode(200)
                .header("Set-Cookie", containsString("seat_session="));
    }

    @Test
    void persistedBidSetCanBeOverwritten() {
        provision("bidder@example.com", true, PASSWORD);
        String session = login("bidder@example.com", PASSWORD, ORIGIN, true)
                .then().statusCode(200).extract().detailedCookie("seat_session").getValue();
        Csrf csrf = csrf();
        Response context = given().cookie("seat_session", session).get("/api/bidding/current")
                .then().statusCode(200).extract().response();
        long roundId = context.jsonPath().getLong("roundId");
        String date = context.jsonPath().getString("days[0].date");

        putJsonWithSession("/api/bidding/current/bids",
                Map.of("roundId", roundId, "bids", List.of(Map.of("date", date, "tokens", 5))), csrf, session)
                .then().statusCode(200).body("bidTotal", equalTo(5));
        putJsonWithSession("/api/bidding/current/bids",
                Map.of("roundId", roundId, "bids", List.of(Map.of("date", date, "tokens", 7))), csrf, session)
                .then().statusCode(200).body("bidTotal", equalTo(7));
    }

    @Test
    void activationExpirationAttemptLimitRegenerationAndCommonPasswordPolicy() {
        provision("activate@example.com", true, null);
        Csrf csrf = csrf();
        postJson("/api/auth/start", Map.of("email", "activate@example.com"), csrf).then().statusCode(200);
        String first = latestCode("activate@example.com");
        QuarkusTransaction.requiringNew().run(() -> {
            var employee = employees.findByEmail("activate@example.com").orElseThrow();
            activations.findByEmployee(employee.id).orElseThrow().lastSentAt = Instant.now().minusSeconds(120);
        });
        postJson("/api/auth/activation/resend", Map.of("email", "activate@example.com"), csrf)
                .then().statusCode(202).header("Retry-After", notNullValue());
        String second = latestCode("activate@example.com");
        assertNotEquals(first, second);
        verify("activate@example.com", first, csrf, 400);
        String wrong = second.equals("000000") ? "000001" : "000000";
        for (int i = 0; i < 5; i++) verify("activate@example.com", wrong, csrf, 400);
        verify("activate@example.com", second, csrf, 400);

        provision("expired@example.com", true, null);
        postJson("/api/auth/start", Map.of("email", "expired@example.com"), csrf).then().statusCode(200);
        String expiredCode = latestCode("expired@example.com");
        QuarkusTransaction.requiringNew().run(() -> {
            var employee = employees.findByEmail("expired@example.com").orElseThrow();
            activations.findByEmployee(employee.id).orElseThrow().codeExpiresAt = Instant.now().minusSeconds(1);
        });
        verify("expired@example.com", expiredCode, csrf, 400);

        provision("policy@example.com", true, null);
        postJson("/api/auth/start", Map.of("email", "policy@example.com"), csrf).then().statusCode(200);
        String code = latestCode("policy@example.com");
        String token = postJson("/api/auth/activation/verify", Map.of("email", "policy@example.com", "code", code), csrf)
                .then().statusCode(200).extract().path("activationToken");
        password(token, "short password", "short password", csrf, 400);
        password(token, "123456789012345", "123456789012345", csrf, 400);
        password(token, "🚺".repeat(129), "🚺".repeat(129), csrf, 400);
        String unicode = "vierzehn zeichen 🚺";
        password(token, unicode, unicode, csrf, 204);
    }

    @Test
    void concurrentPasswordCreationConsumesAuthorizationOnce() throws Exception {
        provision("concurrent@example.com", true, null);
        Csrf csrf = csrf();
        postJson("/api/auth/start", Map.of("email", "concurrent@example.com"), csrf).then().statusCode(200);
        String token = postJson("/api/auth/activation/verify",
                Map.of("email", "concurrent@example.com", "code", latestCode("concurrent@example.com")), csrf)
                .then().statusCode(200).extract().path("activationToken");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> password(token, PASSWORD, PASSWORD, csrf(), null));
            var second = executor.submit(() -> password(token, PASSWORD, PASSWORD, csrf(), null));
            assertEquals(java.util.List.of(204, 400), java.util.stream.Stream.of(first.get(), second.get()).sorted().toList());
        }
        QuarkusTransaction.requiringNew().run(() -> {
            var employee = employees.findByEmail("concurrent@example.com").orElseThrow();
            assertTrue(employee.passwordHash.startsWith("$argon2id$"));
            assertTrue(activations.findByEmployee(employee.id).isEmpty());
        });
    }

    private Csrf csrf() {
        Response response = given().get("/api/auth/csrf").then().statusCode(204).extract().response();
        return new Csrf(response.getDetailedCookie("csrf-token").getValue(), response.header("X-CSRF-TOKEN"));
    }

    private Response postJson(String path, Object body, Csrf csrf) {
        return given().cookie("csrf-token", csrf.cookie).header("X-CSRF-TOKEN", csrf.token)
                .contentType(ContentType.JSON).body(body).post(path);
    }

    private Response postWithSession(String path, Csrf csrf, String session) {
        return given().cookie("csrf-token", csrf.cookie).cookie("seat_session", session)
                .header("X-CSRF-TOKEN", csrf.token).post(path);
    }

    private Response putJsonWithSession(String path, Object body, Csrf csrf, String session) {
        return given().cookie("csrf-token", csrf.cookie).cookie("seat_session", session)
                .header("X-CSRF-TOKEN", csrf.token).contentType(ContentType.JSON).body(body).put(path);
    }

    private Response login(String email, String password, String origin, boolean includeOrigin) {
        var request = given().contentType(ContentType.URLENC).formParam("j_username", email)
                .formParam("j_password", password).header("X-Forwarded-Proto", "https")
                .redirects().follow(false);
        if (includeOrigin) request.header("Origin", origin);
        return request.post("/j_security_check");
    }

    private Response loginWithReferer(String email, String password, String referer) {
        return given().contentType(ContentType.URLENC).header("Referer", referer)
                .header("X-Forwarded-Proto", "https")
                .formParam("j_username", email).formParam("j_password", password)
                .redirects().follow(false).post("/j_security_check");
    }

    private int password(String token, String password, String confirmation, Csrf csrf, Integer expected) {
        int status = postJson("/api/auth/activation/password", Map.of("activationToken", token,
                "password", password, "passwordConfirmation", confirmation), csrf).statusCode();
        if (expected != null) assertEquals(expected.intValue(), status);
        return status;
    }

    private void verify(String email, String code, Csrf csrf, int status) {
        postJson("/api/auth/activation/verify", Map.of("email", email, "code", code), csrf)
                .then().statusCode(status).body("code", equalTo("ACTIVATION_INVALID"));
    }

    private String latestCode(String email) {
        var matcher = Pattern.compile("\\b([0-9]{6})\\b").matcher(mailbox.getMessagesSentTo(email).getLast().getText());
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private void provision(String email, boolean enabled, String password) {
        QuarkusTransaction.requiringNew().run(() -> {
            if (employees.findByEmail(email).isPresent()) return;
            var employee = new EmployeeEntity();
            employee.email = email;
            employee.firstName = "Test";
            employee.lastName = "Employee";
            employee.enabled = enabled;
            if (password != null) {
                employee.passwordHash = passwordHasher.hash(password);
                employee.passwordSetAt = Instant.now();
            }
            employees.persist(employee);
        });
    }

    private record Csrf(String cookie, String token) {}
}
