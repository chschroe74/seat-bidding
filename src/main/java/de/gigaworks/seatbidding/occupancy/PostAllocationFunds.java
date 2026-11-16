package de.gigaworks.seatbidding.occupancy;

public final class PostAllocationFunds {

    private PostAllocationFunds() {
    }

    public static int availableForLateBookings(int grantTokens, int uncappedCarriedInTokens,
            int postAllocationSpend, int committedBidTokens) {
        int unspentCarryIn = Math.max(0, uncappedCarriedInTokens - postAllocationSpend);
        int carryInCommittedToBids = Math.max(0, committedBidTokens - grantTokens);
        return Math.max(0, unspentCarryIn - carryInCommittedToBids);
    }

    public static int effectiveCarriedInTokens(int carriedInTokens, int uncappedCarriedInTokens,
            int postAllocationSpend) {
        return Math.min(carriedInTokens, Math.max(0, uncappedCarriedInTokens - postAllocationSpend));
    }

    public static int effectiveStartingBalance(int grantTokens, int carriedInTokens,
            int uncappedCarriedInTokens, int postAllocationSpend) {
        return Math.addExact(grantTokens,
                effectiveCarriedInTokens(carriedInTokens, uncappedCarriedInTokens, postAllocationSpend));
    }

    public static int regularBalanceReduction(int carriedInTokens, int uncappedCarriedInTokens,
            int postAllocationSpend) {
        return carriedInTokens - effectiveCarriedInTokens(carriedInTokens, uncappedCarriedInTokens,
                postAllocationSpend);
    }
}