package de.gigaworks.seatbidding.allocation;

import jakarta.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class SecureRandomSelector implements RandomSelector {
    
    private final SecureRandom random = new SecureRandom();
    
    @Override
    public <T> List<Draw<T>> drawOrder(List<T> values) {
        var remaining = new ArrayList<>(values);
        var result = new ArrayList<Draw<T>>(values.size());
        while (!remaining.isEmpty()) {
            int index = random.nextInt(remaining.size());
            result.add(new Draw<>(remaining.remove(index), Integer.toUnsignedString(random.nextInt(), 16)));
        }
        return List.copyOf(result);
    }
    
}

