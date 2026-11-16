package de.gigaworks.seatbidding.tokens;

public final class BalanceCalculator {
    
    private BalanceCalculator() {
    }
    
    public static ClosingBalance close(int startingBalance, int successfulBidTokens, int carryOverCap) {
        requireNonNegative("startingBalance", startingBalance);
        requireNonNegative("successfulBidTokens", successfulBidTokens);
        requireNonNegative("carryOverCap", carryOverCap);
        if (successfulBidTokens > startingBalance) {
            throw new IllegalArgumentException("successful bid spend exceeds starting balance");
        }
        int remaining = startingBalance - successfulBidTokens;
        int carry = Math.min(carryOverCap, remaining);
        return new ClosingBalance(remaining, carry, remaining - carry);
    }
    
    public static int nextStartingBalance(int grant, int carriedIn) {
        requireNonNegative("grant", grant);
        requireNonNegative("carriedIn", carriedIn);
        return Math.addExact(grant, carriedIn);
    }

    public static ClosingBalance closeAfterLateBookings(int grantTokens, int carriedInTokens,
            int uncappedCarriedInTokens, int postAllocationSpend, int successfulBidTokens, int carryOverCap) {
        requireNonNegative("grantTokens", grantTokens);
        requireNonNegative("carriedInTokens", carriedInTokens);
        requireNonNegative("uncappedCarriedInTokens", uncappedCarriedInTokens);
        requireNonNegative("postAllocationSpend", postAllocationSpend);
        if (carriedInTokens > uncappedCarriedInTokens || postAllocationSpend > uncappedCarriedInTokens) {
            throw new IllegalArgumentException("carry-in accounting is inconsistent");
        }
        int effectiveCarryIn = Math.min(carriedInTokens, uncappedCarriedInTokens - postAllocationSpend);
        int effectiveStartingBalance = Math.addExact(grantTokens, effectiveCarryIn);
        var regular = close(effectiveStartingBalance, successfulBidTokens, carryOverCap);
        int ledgerRemaining = Math.addExact(grantTokens, uncappedCarriedInTokens)
                - postAllocationSpend - successfulBidTokens;
        return new ClosingBalance(regular.remaining(), regular.carriedOut(),
                ledgerRemaining - regular.carriedOut());
    }
    
    private static void requireNonNegative(String name, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }
    
    public record ClosingBalance(
            int remaining,
            int carriedOut,
            int expired) {
        
    }
    
}