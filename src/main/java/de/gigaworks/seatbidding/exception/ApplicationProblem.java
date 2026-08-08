package de.gigaworks.seatbidding.exception;

import de.gigaworks.seatbidding.dto.ProblemResponse;

import java.util.List;

public class ApplicationProblem extends SeatBiddingException {
    
    private final int status;
    private final String code;
    private final String title;
    private final List<ProblemResponse.Violation> violations;
    private final Long retryAfterSeconds;
    
    public ApplicationProblem(int status, String code, String title, String detail) {
        this(status, code, title, detail, null, null);
    }
    
    public ApplicationProblem(int status, String code, String title, String detail,
            List<ProblemResponse.Violation> violations) {
        this(status, code, title, detail, violations, null);
    }
    
    public ApplicationProblem(int status, String code, String title, String detail,
            List<ProblemResponse.Violation> violations, Long retryAfterSeconds) {
        super(detail);
        this.status = status;
        this.code = code;
        this.title = title;
        this.violations = violations;
        this.retryAfterSeconds = retryAfterSeconds;
    }
    
    public int status() {
        return status;
    }
    
    public String code() {
        return code;
    }
    
    public String title() {
        return title;
    }
    
    public List<ProblemResponse.Violation> violations() {
        return violations;
    }
    
    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
    
    public static ApplicationProblem forbidden(String code, String detail) {
        return new ApplicationProblem(403, code, "Access denied", detail);
    }
    
    public static ApplicationProblem notFound(String code, String detail) {
        return new ApplicationProblem(404, code, "Resource not found", detail);
    }
    
    public static ApplicationProblem conflict(String code, String title, String detail) {
        return new ApplicationProblem(409, code, title, detail);
    }
    
    public static ApplicationProblem badRequest(String code, String title, String detail) {
        return new ApplicationProblem(400, code, title, detail);
    }
    
    public static ApplicationProblem unauthorized(String code, String detail) {
        return new ApplicationProblem(401, code, "Authentication failed", detail);
    }
    
    public static ApplicationProblem serviceUnavailable(String code, String detail) {
        return new ApplicationProblem(503, code, "Service temporarily unavailable", detail);
    }
    
    public static ApplicationProblem tooManyRequests(long retryAfterSeconds) {
        return new ApplicationProblem(429, "RATE_LIMITED", "Too many requests",
                "Too many authentication attempts. Try again later.", null, Math.max(1, retryAfterSeconds));
    }
    
}
