package de.gigaworks.seatbidding.allocation;

import java.util.List;

public interface SeatPriorityRandomSelector {

    <T> Permutation<T> permute(List<T> canonicalValues);

    record Permutation<T>(List<T> values, String auditValue) {
    }
}