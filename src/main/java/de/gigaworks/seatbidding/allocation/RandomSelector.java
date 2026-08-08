package de.gigaworks.seatbidding.allocation;

import java.util.List;

public interface RandomSelector {
    
    <T> List<Draw<T>> drawOrder(List<T> values);
    
    record Draw<T>(
            T value,
            String auditValue) {
        
    }
    
}

