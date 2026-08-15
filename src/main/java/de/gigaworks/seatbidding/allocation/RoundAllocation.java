package de.gigaworks.seatbidding.allocation;

import de.gigaworks.seatbidding.persistence.AllocationUnitType;
import de.gigaworks.seatbidding.persistence.AttendancePeriod;
import java.time.LocalDate;
import java.util.List;

public final class RoundAllocation {

    public static final String ALGORITHM_VERSION = "v4";

    private RoundAllocation() {
    }

    public record TargetDate(long dateId, LocalDate targetDate, Long reservationId,
            int reservedSeatCount, int assignableSeatCapacity) {
        public TargetDate(long dateId, LocalDate targetDate) {
            this(dateId, targetDate, null, 0, -1);
        }
    }

    public record Bid(long dateId, LocalDate targetDate, long bidId, long employeeId,
            int tokens, AttendancePeriod attendancePeriod) {
        public Bid(long dateId, LocalDate targetDate, long bidId, long employeeId, int tokens) {
            this(dateId, targetDate, bidId, employeeId, tokens, AttendancePeriod.FULL_DAY);
        }
    }

    public record Member(long bidId, long employeeId, int tokens,
            AttendancePeriod attendancePeriod, short memberOrder) {
    }

    public record Unit(long dateId, LocalDate targetDate, AllocationUnitType unitType,
            String fairnessIdentity, int scoreTokens, List<Member> members) {
        public Unit {
            members = List.copyOf(members);
        }
    }

    public record Pairing(List<Unit> units, String auditJson) {
        public Pairing {
            units = List.copyOf(units);
        }
    }

    public enum Classification { FIXED_WINNER, FIXED_LOSER, BOUNDARY }

    public record ClassifiedUnit(Unit unit, int scoreRank, Classification classification,
            String boundaryTieGroup) {
    }

    public record ClassifiedDate(long dateId, LocalDate targetDate, Long reservationId,
            int reservedSeatCount, int capacity, int unresolvedSeats, List<ClassifiedUnit> units) {
        public ClassifiedDate {
            units = List.copyOf(units);
        }
    }

    public record Problem(long roundId, int capacity, List<ClassifiedDate> dates, String pairingAudit) {
        public Problem {
            dates = List.copyOf(dates);
        }
    }

    public record Objective(int filledUnresolvedSlots, int distinctTieWinners,
            List<Integer> sortedTieWinCounts) {
        public Objective {
            sortedTieWinCounts = List.copyOf(sortedTieWinCounts);
        }
    }

    public record MemberResult(long bidId, long employeeId, int tokens,
            AttendancePeriod attendancePeriod, short memberOrder, int displayRank) {
    }

    public record Result(long dateId, LocalDate targetDate, AllocationUnitType unitType,
            String fairnessIdentity, int scoreTokens, int scoreRank, int finalRank,
            boolean assigned, AllocationResolution resolution, String boundaryTieGroup,
            String algorithmVersion, List<MemberResult> members) {
        public Result {
            members = List.copyOf(members);
        }
    }

    public record Solution(List<Result> results, Objective objective, String pairingAudit,
            String inputFingerprint, String selectedSolutionFingerprint,
            String capacitySelectionValue, int equivalentSolutionCount) {
        public Solution {
            results = List.copyOf(results);
        }
    }

}