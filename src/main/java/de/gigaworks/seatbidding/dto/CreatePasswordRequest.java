package de.gigaworks.seatbidding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePasswordRequest(
        @NotBlank @Size(max = 128) String activationToken,
        @NotNull String password,
        @NotNull String passwordConfirmation) {
    
}
