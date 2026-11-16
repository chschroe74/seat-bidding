package de.gigaworks.seatbidding.dto;

import de.gigaworks.seatbidding.persistence.AllocationUnitType;
import de.gigaworks.seatbidding.persistence.AttendancePeriod;
import de.gigaworks.seatbidding.persistence.CapacitySource;
import de.gigaworks.seatbidding.persistence.OccupancySourceType;
import de.gigaworks.seatbidding.persistence.OfferCandidateStatus;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "PublishedAssignments")
public record AssignmentsResponse(
        long roundId,
        String status,
        Instant publishedAt,
        CapacitySource capacitySource,
        Long floorPlanVersionId,
        int seatCapacity,
        RelinquishmentCredits myRelinquishmentCredits,
        LateBookingFunds myLateBookingFunds,
        List<AssignmentDay> days) {

    public enum MyStatus { NO_BID, ASSIGNED, NOT_ASSIGNED, RELINQUISHED, CLAIMED_LATER, BOOKED_LATER }

    public record RelinquishmentCredits(int availableTokens, Instant expiresAt) {
    }

    public record LateBookingFunds(int availableTokens, int relinquishmentCreditTokens, int carryInTokens) {
    }

    public record AssignmentDay(
            LocalDate date,
            DayOfWeek weekday,
            Instant targetDateEndAt,
            MyStatus myStatus,
            int reservedSeatCount,
            int assignableSeatCapacity,
            String reservationDescription,
            int originalOccupiedSeatCount,
            int originalAssignedEmployeeCount,
            int morningOccupiedCount,
            int afternoonOccupiedCount,
            int occupiedSeatCount,
            int activeEmployeeCount,
            int assignedEmployeeCount,
            List<Participant> originalParticipants,
            List<Participant> participants,
            List<CurrentOccupant> currentOccupants,
            RelinquishmentAction myRelinquishmentAction,
            List<OfferHistory> myOfferHistory,
            List<OpenBookingOption> openBookingOptions) {
    }

    public record Participant(
            long allocationUnitId,
            AllocationUnitType unitType,
            int unitRank,
            int unitScoreTokens,
            long employeeId,
            String firstName,
            String lastName,
            int tokens,
            AttendancePeriod attendancePeriod,
            boolean assigned,
            int displayRank,
            boolean isCurrentUser,
            Integer seatNumber) {
    }

    public record CurrentOccupant(
            long occupancyId,
            long employeeId,
            String firstName,
            String lastName,
            AttendancePeriod attendancePeriod,
            OccupancySourceType sourceType,
            Integer seatNumber,
            boolean isCurrentUser) {
    }

    public record RelinquishmentAction(long occupancyId, boolean allowed, String disabledReason) {
    }

    public record OfferHistory(
            long offerId,
            long candidateId,
            AttendancePeriod attendancePeriod,
            int requiredTokens,
            Instant deadline,
            OfferCandidateStatus status,
            boolean claimAllowed,
            boolean declineAllowed,
            boolean seatSelectionRequired,
            int availableSeatCount,
            String statusText) {
    }

    public record OpenBookingOption(
            AttendancePeriod attendancePeriod,
            boolean available,
            int requiredTokens,
            boolean bookingAllowed,
            boolean seatSelectionRequired,
            int availableSeatCount,
            String disabledReason) {
    }

}