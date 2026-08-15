package de.gigaworks.seatbidding.allocation;

import jakarta.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;
import java.util.List;

@ApplicationScoped
public class SecurePairingRandomSelector implements PairingRandomSelector {

    private final SecureRandom random = new SecureRandom();

    @Override
    public <T> RandomSelector.Draw<T> select(List<T> canonicalValues) {
        if (canonicalValues.isEmpty()) {
            throw new IllegalArgumentException("at least one value is required");
        }
        int index = random.nextInt(canonicalValues.size());
        return new RandomSelector.Draw<>(canonicalValues.get(index), Integer.toString(index));
    }

}