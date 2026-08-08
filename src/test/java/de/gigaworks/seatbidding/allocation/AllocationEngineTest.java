package de.gigaworks.seatbidding.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class AllocationEngineTest {
    private final AllocationEngine engine = new AllocationEngine(new ReverseSelector());

    @Test void allBiddersWinWhenCapacityIsNotFilled() {
        var result = engine.allocate(List.of(new AllocationEngine.Bid(1, 20), new AllocationEngine.Bid(2, 10)), 4);
        assertTrue(result.stream().allMatch(AllocationEngine.Result::assigned));
        assertTrue(result.stream().allMatch(r -> r.drawValue() == null));
    }

    @Test void onlyBoundaryTieIsDrawn() {
        var result = engine.allocate(List.of(
                new AllocationEngine.Bid(1, 20), new AllocationEngine.Bid(2, 10), new AllocationEngine.Bid(3, 10)), 2);
        assertTrue(result.get(0).assigned());
        assertEquals(3, result.get(1).bidId());
        assertTrue(result.get(1).assigned());
        assertEquals(2, result.get(2).bidId());
        assertFalse(result.get(2).assigned());
        assertNull(result.get(0).drawValue());
        assertEquals("draw-0", result.get(1).drawValue());
    }

    @Test void tieEntirelyBelowBoundaryIsNotDrawn() {
        var result = engine.allocate(List.of(
                new AllocationEngine.Bid(1, 20), new AllocationEngine.Bid(2, 10), new AllocationEngine.Bid(3, 10)), 1);
        assertTrue(result.get(0).assigned());
        assertFalse(result.get(1).assigned());
        assertFalse(result.get(2).assigned());
        assertNull(result.get(1).drawValue());
    }

    private static class ReverseSelector implements RandomSelector {
        @Override public <T> List<Draw<T>> drawOrder(List<T> values) {
            var reversed = new ArrayList<>(values);
            Collections.reverse(reversed);
            var result = new ArrayList<Draw<T>>();
            for (int i = 0; i < reversed.size(); i++) result.add(new Draw<>(reversed.get(i), "draw-" + i));
            return result;
        }
    }
}

