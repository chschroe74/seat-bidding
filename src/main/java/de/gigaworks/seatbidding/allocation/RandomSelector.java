package de.gigaworks.seatbidding.allocation;

import java.util.List;

public interface RandomSelector {

    <T> Draw<T> select(List<T> canonicalValues);

    record Draw<T>(
            T value,
            String auditValue) {

    }

}
