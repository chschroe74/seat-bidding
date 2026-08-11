package de.gigaworks.seatbidding.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "CurrentEmployee")
public record MeResponse(
        long id,
        String firstName,
        String lastName,
        String email,
        boolean isAdmin) {
    
}
