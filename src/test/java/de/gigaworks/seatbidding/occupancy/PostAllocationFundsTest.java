package de.gigaworks.seatbidding.occupancy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PostAllocationFundsTest {

    @Test
    void newGrantCannotFundLateBookings() {
        assertEquals(0, PostAllocationFunds.availableForLateBookings(60, 0, 0, 0));
        assertEquals(60, PostAllocationFunds.availableForLateBookings(60, 60, 0, 0));
    }

    @Test
    void bidCommitmentsUseGrantBeforeSharingUncappedCarryIn() {
        assertEquals(60, PostAllocationFunds.availableForLateBookings(60, 60, 0, 60));
        assertEquals(36, PostAllocationFunds.availableForLateBookings(60, 60, 0, 84));
        assertEquals(20, PostAllocationFunds.availableForLateBookings(60, 60, 40, 60));
    }

    @Test
    void lateSpendingConsumesExcessBeforeReducingRegularCarryIn() {
        assertEquals(24, PostAllocationFunds.effectiveCarriedInTokens(24, 60, 30));
        assertEquals(0, PostAllocationFunds.regularBalanceReduction(24, 60, 30));
        assertEquals(20, PostAllocationFunds.effectiveCarriedInTokens(24, 60, 40));
        assertEquals(4, PostAllocationFunds.regularBalanceReduction(24, 60, 40));
        assertEquals(80, PostAllocationFunds.effectiveStartingBalance(60, 24, 60, 40));
    }
}