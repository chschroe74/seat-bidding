package de.gigaworks.seatbidding.allocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

final class AllocationFingerprints {

    /*
     * Encoding v3 is UTF-8, line-oriented, and uses only stable numeric identifiers,
     * ISO dates, integers, enum names, and the '-' null marker. Dates are chronological;
     * bids and results are ordered by stable bid identifier within each date. A future
     * encoding change must use a new allocation algorithm version.
     */
    private AllocationFingerprints() {
    }

    static String input(RoundAllocation.Problem problem) {
        var value = new StringBuilder("seat-bidding-allocation-input|v3\n")
                .append("round|").append(problem.roundId()).append('|').append(problem.capacity()).append('\n');
        for (var date : problem.dates()) {
            value.append("date|").append(date.dateId()).append('|').append(date.targetDate()).append('|')
                    .append(date.reservationId() == null ? "-" : date.reservationId()).append('|')
                    .append(date.reservedSeatCount()).append('|').append(date.capacity()).append('|')
                    .append(date.unresolvedSeats()).append('\n');
            date.bids().stream().sorted(Comparator.comparingLong(RoundAllocation.ClassifiedBid::bidId))
                    .forEach(bid -> value.append("bid|").append(bid.dateId()).append('|').append(bid.bidId())
                            .append('|').append(bid.employeeId()).append('|').append(bid.tokens()).append('|')
                            .append(bid.tokenRank()).append('|').append(bid.classification()).append('|')
                            .append(bid.boundaryTieGroup() == null ? "-" : bid.boundaryTieGroup()).append('\n'));
        }
        return sha256(value.toString());
    }

    static String solution(java.util.List<RoundAllocation.Result> results) {
        var value = new StringBuilder("seat-bidding-allocation-solution|v3\n");
        results.stream().sorted(Comparator.comparing(RoundAllocation.Result::targetDate)
                        .thenComparingLong(RoundAllocation.Result::dateId)
                        .thenComparingLong(RoundAllocation.Result::bidId))
                .forEach(result -> value.append("result|").append(result.dateId()).append('|')
                        .append(result.bidId()).append('|').append(result.assigned()).append('|')
                        .append(result.tokenRank()).append('|').append(result.finalRank()).append('|')
                        .append(result.resolution()).append('|')
                        .append(result.boundaryTieGroup() == null ? "-" : result.boundaryTieGroup()).append('\n'));
        return sha256(value.toString());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

}
