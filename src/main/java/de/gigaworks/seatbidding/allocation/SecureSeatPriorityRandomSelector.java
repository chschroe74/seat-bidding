package de.gigaworks.seatbidding.allocation;

import jakarta.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

@ApplicationScoped
public class SecureSeatPriorityRandomSelector implements SeatPriorityRandomSelector {

    private final SecureRandom random = new SecureRandom();

    @Override
    public <T> Permutation<T> permute(List<T> canonicalValues) {
        long seed = random.nextLong();
        var shuffled = new ArrayList<>(canonicalValues);
        var seeded = new SplittableRandom(seed);
        for (int index = shuffled.size() - 1; index > 0; index--) {
            int selected = seeded.nextInt(index + 1);
            T value = shuffled.get(index);
            shuffled.set(index, shuffled.get(selected));
            shuffled.set(selected, value);
        }
        return new Permutation<>(List.copyOf(shuffled), Long.toUnsignedString(seed, 16));
    }
}