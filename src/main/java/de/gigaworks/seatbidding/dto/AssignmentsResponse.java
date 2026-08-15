package de.gigaworks.seatbidding.dto;

import de.gigaworks.seatbidding.persistence.AllocationUnitType;
import de.gigaworks.seatbidding.persistence.AttendancePeriod;

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
        int seatCapacity,
        List<AssignmentDay> days) {
    
    public enum MyStatus {NO_BID, ASSIGNED, NOT_ASSIGNED}
    
    public record AssignmentDay(
            LocalDate date,
            DayOfWeek weekday,
            MyStatus myStatus,
            int reservedSeatCount,
            int assignableSeatCapacity,
            String reservationDescription,
            int occupiedSeatCount,
            int assignedEmployeeCount,
            List<Participant> participants) {
        
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
            boolean isCurrentUser) {
        
    }
    

}