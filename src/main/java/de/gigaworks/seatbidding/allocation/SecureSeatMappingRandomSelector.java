package de.gigaworks.seatbidding.allocation;

import jakarta.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;

@ApplicationScoped
public class SecureSeatMappingRandomSelector implements SeatMappingRandomSelector {

    private final SecureRandom random = new SecureRandom();

    @Override
    public Draw draw() {
        long seed = random.nextLong();
        return new Draw(seed, Long.toUnsignedString(seed, 16));
    }
}