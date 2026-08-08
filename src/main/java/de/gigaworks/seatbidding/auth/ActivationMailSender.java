package de.gigaworks.seatbidding.auth;

import java.time.Instant;

public interface ActivationMailSender {
    
    void send(String email, String code, Instant expiresAt);
    
}
