package de.gigaworks.seatbidding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Problem")
public record ProblemResponse(
        String type,
        String title,
        int status,
        String code,
        String detail,
        String instance,
        List<Violation> violations,
        String traceId) {
    
    public record Violation(
            String field,
            String message) {
        
    }
    
}

