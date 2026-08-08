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

