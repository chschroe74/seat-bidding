package de.gigaworks.seatbidding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ActivationVerifyRequest(
        @NotBlank @Size(max = 512) String email,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
    
}
