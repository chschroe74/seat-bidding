package de.gigaworks.seatbidding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthenticationStartRequest(
        @NotBlank @Size(max = 512) String email) {
    
}
