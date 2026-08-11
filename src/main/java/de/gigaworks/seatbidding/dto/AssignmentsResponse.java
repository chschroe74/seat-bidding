package de.gigaworks.seatbidding.dto;

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
            int assignedCount,
            int reservedSeatCount,
            int assignableSeatCapacity,
            String reservationDescription,
            List<Participant> participants) {
        
    }
    
    public record Participant(
            long employeeId,
            String firstName,
            String lastName,
            int tokens,
            boolean assigned,
            int rank,
            boolean isCurrentUser) {
        
    }
    
}
