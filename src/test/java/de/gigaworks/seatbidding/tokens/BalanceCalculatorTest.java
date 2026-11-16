package de.gigaworks.seatbidding.tokens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class BalanceCalculatorTest {
    @Test void successfulBidIsChargedAndCarryIsCapped() {
        assertEquals(new BalanceCalculator.ClosingBalance(50, 20, 30), BalanceCalculator.close(70, 20, 20));
    }
    @Test void unsuccessfulBidIsNotIncludedInSpend() {
        assertEquals(new BalanceCalculator.ClosingBalance(70, 20, 50), BalanceCalculator.close(70, 0, 20));
    }
    @Test void rejectsOverspend() {
        assertThrows(IllegalArgumentException.class, () -> BalanceCalculator.close(10, 11, 20));
    }
    @Test void uncappedLateCarryIsSpentBeforeItReducesRegularCarry() {
        assertEquals(new BalanceCalculator.ClosingBalance(84, 24, 66),
                BalanceCalculator.closeAfterLateBookings(60, 24, 60, 30, 0, 24));
        assertEquals(new BalanceCalculator.ClosingBalance(20, 20, 0),
                BalanceCalculator.closeAfterLateBookings(60, 24, 60, 40, 60, 24));
    }
}