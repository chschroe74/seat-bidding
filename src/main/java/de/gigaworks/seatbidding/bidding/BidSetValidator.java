package de.gigaworks.seatbidding.bidding;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BidSetValidator {
    
    private BidSetValidator() {
    }
    
    public static Map<LocalDate, Integer> validateAndNormalize(
            List<SubmittedBid> submitted, Set<LocalDate> roundDates, int startingBalance) {
        if (submitted == null) {
            throw new BidValidationException("BIDS_REQUIRED", "bids are required");
        }
        if (submitted.size() > 5) {
            throw new BidValidationException("TOO_MANY_BIDS", "at most five bids are allowed");
        }
        var seen = new HashSet<LocalDate>();
        var normalized = new java.util.LinkedHashMap<LocalDate, Integer>();
        long total = 0;
        for (var bid : submitted) {
            if (bid == null || bid.date() == null) {
                throw new BidValidationException("INVALID_BID", "bid date is required");
            }
            if (!seen.add(bid.date())) {
                throw new BidValidationException("DUPLICATE_BID_DATE", "duplicate bid date");
            }
            if (!roundDates.contains(bid.date())) {
                throw new BidValidationException("DATE_OUTSIDE_ROUND", "date is outside the open round");
            }
            if (bid.tokens() < 0) {
                throw new BidValidationException("NEGATIVE_BID", "tokens must be non-negative");
            }
            if (bid.tokens() > 0) {
                normalized.put(bid.date(), bid.tokens());
                total += bid.tokens();
            }
        }
        if (total > startingBalance) {
            throw new BidValidationException("BID_BUDGET_EXCEEDED", "bid total exceeds starting balance");
        }
        return Map.copyOf(normalized);
    }
    
    public record SubmittedBid(
            LocalDate date,
            int tokens) {
        
    }
    
}
