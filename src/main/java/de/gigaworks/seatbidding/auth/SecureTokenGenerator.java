package de.gigaworks.seatbidding.auth;

import jakarta.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;
import java.util.Base64;

@ApplicationScoped
public class SecureTokenGenerator {
    
    private final SecureRandom random;
    
    public SecureTokenGenerator() {
        this(new SecureRandom());
    }
    
    SecureTokenGenerator(SecureRandom random) {
        this.random = random;
    }
    
    public String activationCode() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }
    
    public String opaqueToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
}
