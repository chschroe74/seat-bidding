package de.gigaworks.seatbidding.resource;

import de.gigaworks.seatbidding.dto.ProblemResponse;
import de.gigaworks.seatbidding.exception.ApplicationProblem;
import io.opentelemetry.api.trace.Span;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.PessimisticLockException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

@Slf4j
public class ExceptionMappers {
    
    private static final String MIME_TYPE = "application/problem+json";
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    private static final String REJECTED_LOG_MESSAGE =
            "operation=http-request outcome=rejected path={} status={} code={} traceId={}";
    private static final Set<String> OPTIONAL_BROWSER_PROBES = Set.of(
            "/flutter.js.map",
            "/favicon.ico",
            "/apple-touch-icon.png",
            "/apple-touch-icon-precomposed.png",
            "/apple-touch-icon-120x120.png",
            "/apple-touch-icon-120x120-precomposed.png");
    
    @ServerExceptionMapper
    public Response mapApplicationProblem(ApplicationProblem problem, UriInfo uriInfo) {
        String traceId = currentTraceId();
        var body = new ProblemResponse(problemType(problem.code()), problem.title(), problem.status(), problem.code(),
                problem.getMessage(), uriInfo.getRequestUri().getPath(), problem.violations(), traceId);
        var response = Response.status(problem.status()).type(MIME_TYPE).entity(body)
                .header("Cache-Control", "no-store");
        warnRejected(uriInfo, problem.status(), problem.code(), traceId);
        if (problem.retryAfterSeconds() != null) {
            response.header("Retry-After", problem.retryAfterSeconds());
        }
        return response.build();
    }
    
    @ServerExceptionMapper
    public Response mapConstraintViolation(ConstraintViolationException exception, UriInfo uriInfo) {
        var violations = exception.getConstraintViolations().stream()
                .map(violation -> new ProblemResponse.Violation(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        String traceId = currentTraceId();
        var body = new ProblemResponse(problemType("VALIDATION_FAILED"), "Validation failed", 400,
                "VALIDATION_FAILED", "The request contains invalid values.", uriInfo.getPath(), violations, traceId);
        warnValidationRejected(uriInfo, violations.size(), traceId);
        return Response.status(400).type(MIME_TYPE).entity(body).build();
    }
    
    @ServerExceptionMapper
    public Response mapPersistence(PersistenceException exception, UriInfo uriInfo) {
        boolean lock = containsLockFailure(exception);
        int status = lock ? 409 : 503;
        String code = lock ? "CONCURRENT_UPDATE" : "DATABASE_UNAVAILABLE";
        String title = lock ? "Concurrent update" : "Service temporarily unavailable";
        String traceId = currentTraceId();
        var body = new ProblemResponse(problemType(code), title, status, code,
                lock ? "Another update is in progress; retry shortly." : "The database is temporarily unavailable.",
                uriInfo.getPath(), null, traceId);
        if (lock) {
            warnRejected(uriInfo, status, code, traceId);
        }
        else {
            errorFailed(uriInfo, status, code, traceId, exception);
        }
        return Response.status(status).type(MIME_TYPE).entity(body).build();
    }
    
    @ServerExceptionMapper
    public Response mapWebApplication(WebApplicationException exception, UriInfo uriInfo) {
        int status = exception.getResponse().getStatus();
        String code = switch (status) {
            case 400 ->
                    "MALFORMED_REQUEST";
            case 401 ->
                    "AUTHENTICATION_REQUIRED";
            case 403 ->
                    "ACCESS_DENIED";
            case 404 ->
                    "RESOURCE_NOT_FOUND";
            default ->
                    "REQUEST_FAILED";
        };
        String traceId = currentTraceId();
        var body = new ProblemResponse(problemType(code), exception.getResponse().getStatusInfo().getReasonPhrase(),
                status, code,
                status >= 500 ? "The server could not complete the request." : "The request could not be completed.",
                uriInfo.getPath(), null, traceId);
        warnRejected(uriInfo, status, code, traceId);
        return Response.fromResponse(exception.getResponse()).type(MIME_TYPE).entity(body).build();
    }
    
    @ServerExceptionMapper
    public Response mapUnexpected(Throwable exception, UriInfo uriInfo) {
        String traceId = currentTraceId();
        errorFailed(uriInfo, 500, INTERNAL_ERROR, traceId, exception);
        var body = new ProblemResponse(problemType(INTERNAL_ERROR), "Internal server error", 500,
                INTERNAL_ERROR, "The server could not complete the request.", uriInfo.getPath(), null, traceId);
        return Response.serverError().type(MIME_TYPE).entity(body).build();
    }
    
    private static boolean containsLockFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof LockTimeoutException || current instanceof PessimisticLockException) {
                return true;
            }
        }
        return false;
    }

    private static void warnRejected(UriInfo uriInfo, int status, String code, String traceId) {
        String path = uriInfo.getPath();
        if (status == 404 && isOptionalBrowserProbe(path)) {
            log.debug(REJECTED_LOG_MESSAGE, path, status, code, traceId);
        }
        else {
            log.warn(REJECTED_LOG_MESSAGE, path, status, code, traceId);
        }
    }

    static boolean isOptionalBrowserProbe(String path) {
        String absolutePath = path.startsWith("/") ? path : "/" + path;
        return OPTIONAL_BROWSER_PROBES.contains(absolutePath);
    }

    private static void warnValidationRejected(UriInfo uriInfo, int violationCount, String traceId) {
        log.warn("operation=http-request outcome=rejected path={} status=400 code=VALIDATION_FAILED violations={} traceId={}",
                uriInfo.getPath(), violationCount, traceId);
    }

    private static void errorFailed(UriInfo uriInfo, int status, String code, String traceId, Throwable exception) {
        log.error("operation=http-request outcome=failed path={} status={} code={} traceId={}",
                uriInfo.getPath(), status, code, traceId, exception);
    }
    
    private static String currentTraceId() {
        var spanContext = Span.current().getSpanContext();
        return spanContext.isValid() ? spanContext.getTraceId() : null;
    }
    
    private static String problemType(String code) {
        return "https://seat-app.example/problems/" + code.toLowerCase(Locale.ROOT).replace('_', '-');
    }
    
}