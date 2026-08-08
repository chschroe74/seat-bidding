package de.gigaworks.seatbidding.auth;

import de.gigaworks.seatbidding.dto.ProblemResponse;
import io.opentelemetry.api.trace.Span;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
public class SecurityResponseFilter implements ContainerResponseFilter {
    
    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        String path = request.getUriInfo().getRequestUri().getPath();
        if (path.startsWith("/api/")) {
            response.getHeaders().putSingle("Cache-Control", "no-store");
            mapEmptyBadRequest(request, response, path);
        }
        response.getHeaders().putSingle("X-Content-Type-Options", "nosniff");
        response.getHeaders().putSingle("Referrer-Policy", "no-referrer");
        response.getHeaders().putSingle("Content-Security-Policy", "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; connect-src 'self'; img-src 'self' data: blob:; font-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval'; worker-src 'self' blob:");
    }

    private void mapEmptyBadRequest(ContainerRequestContext request, ContainerResponseContext response, String path) {
        if (response.getStatus() != 400 || response.hasEntity()) {
            return;
        }
        var spanContext = Span.current().getSpanContext();
        String traceId = spanContext.isValid() ? spanContext.getTraceId() : null;
        var body = new ProblemResponse("https://seat-app.example/problems/request-rejected", "Request rejected", 400,
                "REQUEST_REJECTED", "Request security or syntax validation failed.", path, null, traceId);
        response.setEntity(body, null, MediaType.valueOf("application/problem+json"));
        log.warn("operation=http-request outcome=rejected method={} path={} status=400 code=REQUEST_REJECTED traceId={}",
                request.getMethod(), path, traceId);
    }
    
}
