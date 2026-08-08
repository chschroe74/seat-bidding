package de.gigaworks.seatbidding.dto;

import java.time.Instant;

public record ActivationVerifyResponse(
        String activationToken,
        Instant expiresAt) {
    
}
