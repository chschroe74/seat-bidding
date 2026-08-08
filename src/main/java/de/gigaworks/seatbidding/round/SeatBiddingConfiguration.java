package de.gigaworks.seatbidding.round;

import de.gigaworks.seatbidding.auth.AuthenticationConfiguration;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "seat-bidding")
public interface SeatBiddingConfiguration {
    
    @WithName("tokens-per-round")
    @Min(0)
    int tokensPerRound();
    
    @WithName("carry-over-cap")
    @Min(0)
    int carryOverCap();
    
    @WithName("seat-capacity")
    @Min(1)
    int seatCapacity();
    
    @NotNull
    Duration lockTimeout();
    
    @Valid
    Scheduler scheduler();
    
    @WithName("public-client")
    @Valid
    PublicClient publicClient();
    
    @Valid
    AuthenticationConfiguration authentication();
    
    interface Scheduler {
        
        @NotBlank
        String cron();
        
        @WithName("time-zone")
        @NotNull
        ZoneId timeZone();
        
        @WithDefault("true")
        boolean enabled();
        
    }
    
    interface PublicClient {
        
        @WithName("android-download-url")
        Optional<String> androidDownloadUrl();
        
    }
    
}
