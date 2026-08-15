package de.gigaworks.seatbidding.allocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

final class AllocationFingerprints {
    /*
     * Encoding v4 is UTF-8 and line-oriented. Dates are chronological, units use
     * canonical fairness identities, and members use stable bid identifiers.
     * Descriptions and database/query ordering are deliberately excluded.
     */
    private AllocationFingerprints() {
    }

    static String input(RoundAllocation.Problem problem) {
        var value = new StringBuilder("seat-bidding-allocation-input|v4\n")
                .append("round|").append(problem.roundId()).append('|').append(problem.capacity()).append('\n')
                .append("pairing|").append(problem.pairingAudit()).append('\n');
        for (var date : problem.dates()) {
            value.append("date|").append(date.dateId()).append('|').append(date.targetDate()).append('|')
                    .append(date.reservationId() == null ? "-" : date.reservationId()).append('|')
                    .append(date.reservedSeatCount()).append('|').append(date.capacity()).append('|')
                    .append(date.unresolvedSeats()).append('\n');
            date.units().stream().sorted(Comparator.comparing(unit -> unit.unit().fairnessIdentity()))
                    .forEach(unit -> {
                        value.append("unit|").append(unit.unit().fairnessIdentity()).append('|')
                                .append(unit.unit().unitType()).append('|').append(unit.unit().scoreTokens()).append('|')
                                .append(unit.scoreRank()).append('|').append(unit.classification()).append('|')
                                .append(unit.boundaryTieGroup() == null ? "-" : unit.boundaryTieGroup()).append('\n');
                        unit.unit().members().stream().sorted(Comparator.comparingLong(RoundAllocation.Member::bidId))
                                .forEach(member -> value.append("member|").append(member.bidId()).append('|')
                                        .append(member.employeeId()).append('|').append(member.tokens()).append('|')
                                        .append(member.attendancePeriod()).append('|').append(member.memberOrder()).append('\n'));
                    });
        }
        return sha256(value.toString());
    }

    static String solution(java.util.List<RoundAllocation.Result> results) {
        var value = new StringBuilder("seat-bidding-allocation-solution|v4\n");
        results.stream().sorted(Comparator.comparing(RoundAllocation.Result::targetDate)
                        .thenComparingLong(RoundAllocation.Result::dateId)
                        .thenComparing(RoundAllocation.Result::fairnessIdentity))
                .forEach(result -> {
                    value.append("result|").append(result.dateId()).append('|')
                            .append(result.fairnessIdentity()).append('|').append(result.unitType()).append('|')
                            .append(result.scoreTokens()).append('|').append(result.assigned()).append('|')
                            .append(result.scoreRank()).append('|').append(result.finalRank()).append('|')
                            .append(result.resolution()).append('|')
                            .append(result.boundaryTieGroup() == null ? "-" : result.boundaryTieGroup()).append('\n');
                    result.members().stream().sorted(Comparator.comparingLong(RoundAllocation.MemberResult::bidId))
                            .forEach(member -> value.append("member-result|").append(member.bidId()).append('|')
                                    .append(member.attendancePeriod()).append('|').append(member.memberOrder()).append('|')
                                    .append(member.displayRank()).append('\n'));
                });
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