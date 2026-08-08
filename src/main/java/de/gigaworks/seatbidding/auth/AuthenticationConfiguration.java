package de.gigaworks.seatbidding.auth;

import io.smallrye.config.WithName;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;

public interface AuthenticationConfiguration {
    
    @Valid
    Activation activation();
    
    @Valid
    Password password();
    
    @WithName("rate-limit")
    @Valid
    RateLimit rateLimit();
    
    @WithName("allowed-web-origins")
    @NotEmpty
    List<String> allowedWebOrigins();
    
    interface Activation {
        
        @WithName("code-ttl")
        @NotNull
        Duration codeTtl();
        
        @WithName("maximum-attempts")
        @Min(1)
        int maximumAttempts();
        
        @WithName("resend-cooldown")
        @NotNull
        Duration resendCooldown();
        
        @WithName("token-ttl")
        @NotNull
        Duration tokenTtl();
        
        @WithName("code-pepper")
        @NotBlank
        String codePepper();
        
    }
    
    interface Password {
        
        @WithName("minimum-length")
        @Min(15)
        int minimumLength();
        
        @WithName("maximum-length")
        @Min(128)
        int maximumLength();
        
        @WithName("blocklist-resource")
        @NotBlank
        String blocklistResource();
        
        @WithName("argon2-memory-kib")
        @Min(1024)
        int argon2MemoryKib();
        
        @WithName("argon2-iterations")
        @Min(1)
        int argon2Iterations();
        
        @WithName("argon2-parallelism")
        @Min(1)
        int argon2Parallelism();
        
    }
    
    interface RateLimit {
        
        @WithName("start-per-hour")
        @Min(1)
        int startPerHour();
        
        @WithName("resend-per-hour")
        @Min(1)
        int resendPerHour();
        
        @WithName("verify-per-fifteen-minutes")
        @Min(1)
        int verifyPerFifteenMinutes();
        
        @WithName("login-per-fifteen-minutes")
        @Min(1)
        int loginPerFifteenMinutes();
        
        @WithName("source-per-minute")
        @Min(1)
        int sourcePerMinute();
        
    }
    
}
