package de.gigaworks.seatbidding.allocation;

import java.time.LocalDate;
import java.util.List;

public final class RoundAllocation {

    public static final String ALGORITHM_VERSION = "v2";

    private RoundAllocation() {
    }

    public record TargetDate(
            long dateId,
            LocalDate targetDate) {

    }

    public record Bid(
            long dateId,
            LocalDate targetDate,
            long bidId,
            long employeeId,
            int tokens) {

    }

    public enum Classification {
        FIXED_WINNER,
        FIXED_LOSER,
        BOUNDARY
    }

    public record ClassifiedBid(
            long dateId,
            LocalDate targetDate,
            long bidId,
            long employeeId,
            int tokens,
            int tokenRank,
            Classification classification,
            String boundaryTieGroup) {

    }

    public record ClassifiedDate(
            long dateId,
            LocalDate targetDate,
            int capacity,
            int unresolvedSeats,
            List<ClassifiedBid> bids) {

        public ClassifiedDate {
            bids = List.copyOf(bids);
        }

    }

    public record Problem(
            long roundId,
            int capacity,
            List<ClassifiedDate> dates) {

        public Problem {
            dates = List.copyOf(dates);
        }

    }

    public record Objective(
            int filledUnresolvedSlots,
            int distinctTieWinners,
            List<Integer> sortedTieWinCounts) {

        public Objective {
            sortedTieWinCounts = List.copyOf(sortedTieWinCounts);
        }

    }

    public record Result(
            long dateId,
            LocalDate targetDate,
            long bidId,
            long employeeId,
            int tokens,
            int tokenRank,
            int finalRank,
            boolean assigned,
            AllocationResolution resolution,
            String boundaryTieGroup,
            String algorithmVersion) {

    }

    public record Solution(
            List<Result> results,
            Objective objective,
            String inputFingerprint,
            String selectedSolutionFingerprint,
            String randomSelectionValue,
            int equivalentSolutionCount) {

        public Solution {
            results = List.copyOf(results);
        }

    }

}
