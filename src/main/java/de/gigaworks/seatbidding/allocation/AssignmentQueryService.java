package de.gigaworks.seatbidding.allocation;

import de.gigaworks.seatbidding.auth.EmployeeIdentityService;
import de.gigaworks.seatbidding.dto.AssignmentsResponse;
import de.gigaworks.seatbidding.exception.ApplicationProblem;
import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.persistence.RoundDateRepository;
import de.gigaworks.seatbidding.persistence.SeatAssignmentRepository;
import de.gigaworks.seatbidding.persistence.SeatReservationRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AssignmentQueryService {
    
    @Inject
    EmployeeIdentityService identity;
    
    @Inject
    BiddingRoundRepository rounds;
    
    @Inject
    RoundDateRepository dates;
    
    @Inject
    SeatAssignmentRepository assignments;

    @Inject
    SeatReservationRepository reservations;
    
    @Transactional
    public AssignmentsResponse latest() {
        var current = identity.resolve();
        var round = rounds.findLatestCompleted().orElseThrow(() ->
                ApplicationProblem.notFound("PUBLISHED_ROUND_NOT_FOUND", "No assignments have been published yet."));
        var days = dates.findForRound(round.id).stream().map(day -> {
            var results = assignments.findForDate(day.id);
            var mine = results.stream().filter(a -> a.bid.participation.employee.id.equals(current.id)).findFirst();
            var myStatus = mine.map(a -> a.assigned ? AssignmentsResponse.MyStatus.ASSIGNED
                            : AssignmentsResponse.MyStatus.NOT_ASSIGNED)
                    .orElse(AssignmentsResponse.MyStatus.NO_BID);
            var participants = results.stream().map(a -> {
                var employee = a.bid.participation.employee;
                return new AssignmentsResponse.Participant(employee.id, employee.firstName, employee.lastName, a.bid.tokens,
                        a.assigned, a.finalRank, employee.id.equals(current.id));
            }).toList();
            int assignedCount = (int) results.stream().filter(a -> a.assigned).count();
            var reservation = reservations.findByTargetDate(day.targetDate).orElse(null);
            int reserved = reservation == null ? 0 : reservation.reservedSeatCount;
            return new AssignmentsResponse.AssignmentDay(day.targetDate, day.targetDate.getDayOfWeek(),
                    myStatus, assignedCount, reserved, round.seatCapacity - reserved,
                    reservation == null ? null : reservation.description, participants);
        }).toList();
        return new AssignmentsResponse(round.id, round.status.name(), round.processedAt, round.seatCapacity, days);
    }
    
}
