package de.gigaworks.seatbidding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record RegisterPushDeviceRequest(
        @NotBlank @Size(max = 4096) String endpoint,
        @NotNull @Valid Keys keys,
        Instant expirationTime,
        @NotBlank @Size(max = 120) String deviceLabel) {

    public record Keys(@NotBlank @Size(max = 512) String p256dh, @NotBlank @Size(max = 512) String auth) {
    }

}