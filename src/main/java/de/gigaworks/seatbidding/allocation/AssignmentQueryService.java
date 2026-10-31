package de.gigaworks.seatbidding.allocation;

import de.gigaworks.seatbidding.auth.EmployeeIdentityService;
import de.gigaworks.seatbidding.dto.AssignmentsResponse;
import de.gigaworks.seatbidding.exception.ApplicationProblem;
import de.gigaworks.seatbidding.occupancy.PostAllocationService;
import de.gigaworks.seatbidding.persistence.AttendancePeriod;
import de.gigaworks.seatbidding.persistence.BiddingRoundEntity;
import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.persistence.CapacitySource;
import de.gigaworks.seatbidding.persistence.OfferCandidateStatus;
import de.gigaworks.seatbidding.persistence.RelinquishmentCreditRepository;
import de.gigaworks.seatbidding.persistence.RoundDateCapacityStateRepository;
import de.gigaworks.seatbidding.persistence.RoundDateRepository;
import de.gigaworks.seatbidding.persistence.SeatAssignmentRepository;
import de.gigaworks.seatbidding.persistence.SeatOccupancyRepository;
import de.gigaworks.seatbidding.persistence.SeatOfferCandidateRepository;
import de.gigaworks.seatbidding.persistence.SeatReservationRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;

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

    @Inject
    RoundDateCapacityStateRepository capacityStates;

    @Inject
    SeatOccupancyRepository occupancies;

    @Inject
    SeatOfferCandidateRepository candidates;

    @Inject
    RelinquishmentCreditRepository credits;

    @Inject
    PostAllocationService postAllocation;

    @Inject
    Clock clock;

    @Transactional
    public AssignmentsResponse latest() {
        var round = rounds.findLatestCompleted().orElseThrow(() ->
                ApplicationProblem.notFound("PUBLISHED_ROUND_NOT_FOUND", "No assignments have been published yet."));
        return response(round);
    }

    @Transactional
    public AssignmentsResponse round(long roundId) {
        var round = rounds.findById(roundId);
        if (round == null || round.status != de.gigaworks.seatbidding.persistence.RoundStatus.COMPLETED) {
            throw ApplicationProblem.notFound("PUBLISHED_ROUND_NOT_FOUND", "The completed round was not found.");
        }
        var latest = rounds.findLatestCompleted().orElse(null);
        boolean latestRound = latest != null && latest.id.equals(round.id);
        boolean actionable = dates.findForRound(round.id).stream()
                .anyMatch(value -> clock.instant().isBefore(targetDateEnd(value.targetDate, round.scheduleZone)));
        if (!latestRound && !actionable) {
            throw ApplicationProblem.notFound("PUBLISHED_ROUND_NOT_FOUND", "The completed round is no longer addressable.");
        }
        return response(round);
    }

    @Transactional
    public AssignmentsResponse.AssignmentDay day(long roundId, LocalDate date) {
        return round(roundId).days().stream().filter(value -> value.date().equals(date)).findFirst()
                .orElseThrow(() -> ApplicationProblem.notFound("ROUND_DATE_NOT_FOUND", "The round date was not found."));
    }

    private AssignmentsResponse response(BiddingRoundEntity round) {
        var current = identity.resolve();
        var now = clock.instant();
        postAllocation.materializeExpiredCredits(current.id, round.id, now);
        int creditTokens = credits.available(current.id, round.id, now);
        Instant creditExpiry = creditTokens == 0 ? null : dates.findForRound(round.id).stream()
                .max(Comparator.comparing(value -> value.targetDate))
                .map(value -> targetDateEnd(value.targetDate, round.scheduleZone)).orElse(null);
        var days = dates.findForRound(round.id).stream().map(day -> {
            var results = assignments.findForDate(day.id);
            var active = occupancies.findActiveForDate(day.id);
            var mine = results.stream().filter(a -> a.bid.participation.employee.id.equals(current.id)).findFirst();
            var myActive = active.stream().filter(value -> value.employee.id.equals(current.id)).findFirst();
            var myStatus = myActive.map(value -> switch (value.sourceType) {
                case ORIGINAL_ASSIGNMENT -> AssignmentsResponse.MyStatus.ASSIGNED;
                case WAITLIST_CLAIM -> AssignmentsResponse.MyStatus.CLAIMED_LATER;
                case OPEN_BOOKING, ORIGINAL_REBOOKING -> AssignmentsResponse.MyStatus.BOOKED_LATER;
            }).orElseGet(() -> mine.map(value -> {
                if (!value.assigned) {
                    return AssignmentsResponse.MyStatus.NOT_ASSIGNED;
                }
                return occupancies.findOriginal(value.id)
                        .filter(occupancy -> occupancy.status
                                == de.gigaworks.seatbidding.persistence.OccupancyStatus.RELINQUISHED)
                        .map(occupancy -> AssignmentsResponse.MyStatus.RELINQUISHED)
                        .orElse(AssignmentsResponse.MyStatus.ASSIGNED);
            }).orElse(AssignmentsResponse.MyStatus.NO_BID));
            var participants = results.stream().map(a -> {
                var employee = a.bid.participation.employee;
                return new AssignmentsResponse.Participant(a.allocationUnit.id, a.allocationUnit.unitType,
                        a.allocationUnit.finalRank, a.allocationUnit.scoreTokens, employee.id,
                        employee.firstName, employee.lastName, a.bid.tokens, a.attendancePeriod,
                        a.assigned, a.displayRank, employee.id.equals(current.id),
                        employee.id.equals(current.id) && a.allocationUnit.physicalSeat != null
                                ? a.allocationUnit.physicalSeat.seatNumber : null);
            }).toList();
            var currentOccupants = active.stream().map(value -> {
                boolean self = value.employee.id.equals(current.id);
                return new AssignmentsResponse.CurrentOccupant(value.id, value.employee.id,
                        value.employee.firstName, value.employee.lastName, value.attendancePeriod, value.sourceType,
                        self && value.physicalSeat != null ? value.physicalSeat.seatNumber : null, self);
            }).toList();
            int originalAssignedEmployeeCount = (int) results.stream().filter(a -> a.assigned).count();
            int originalOccupiedSeatCount = (int) results.stream().filter(a -> a.assigned)
                    .map(a -> a.allocationUnit.id).distinct().count();
            var reservation = reservations.findByTargetDate(day.targetDate).orElse(null);
            int reserved = reservation == null ? 0 : reservation.reservedSeatCount;
            var state = capacityStates.findForDate(day.id).orElse(null);
            int morning = state == null ? 0 : state.morningOccupied;
            int afternoon = state == null ? 0 : state.afternoonOccupied;
            int occupiedSeatCount = round.capacitySource == CapacitySource.CONFIGURATION
                    ? Math.max(morning, afternoon)
                    : (int) active.stream().filter(value -> value.physicalSeat != null)
                            .map(value -> value.physicalSeat.id).distinct().count();
            var action = myActive.map(value -> new AssignmentsResponse.RelinquishmentAction(value.id,
                    now.isBefore(targetDateEnd(day.targetDate, round.scheduleZone)),
                    now.isBefore(targetDateEnd(day.targetDate, round.scheduleZone)) ? null : "TARGET_DATE_ENDED"))
                    .orElse(null);
            var history = candidates.findForEmployeeDate(current.id, day.id).stream().map(value -> {
                boolean pending = value.status == OfferCandidateStatus.PENDING && now.isBefore(value.offer.deadline);
                return new AssignmentsResponse.OfferHistory(value.offer.id, value.id, value.attendancePeriod,
                        value.requiredTokens, value.offer.deadline, value.status, pending, pending,
                        pending && round.capacitySource == CapacitySource.FLOOR_PLAN,
                        pending ? postAllocation.availabilityCount(day, value.attendancePeriod) : 0,
                        statusText(value.status));
            }).toList();
            var bookingOptions = java.util.Arrays.stream(AttendancePeriod.values()).map(period -> {
                var eligibility = postAllocation.bookingEligibility(day, current, period, now);
                return new AssignmentsResponse.OpenBookingOption(period, eligibility.allowed(), eligibility.price(),
                        eligibility.allowed(), eligibility.allowed() && round.capacitySource == CapacitySource.FLOOR_PLAN,
                        eligibility.allowed() ? postAllocation.availabilityCount(day, period) : 0,
                        eligibility.reason());
            }).toList();
            return new AssignmentsResponse.AssignmentDay(day.targetDate, day.targetDate.getDayOfWeek(),
                    targetDateEnd(day.targetDate, round.scheduleZone), myStatus, reserved, round.seatCapacity - reserved,
                    reservation == null ? null : reservation.description,
                    originalOccupiedSeatCount, originalAssignedEmployeeCount, morning, afternoon,
                    occupiedSeatCount, active.size(), originalAssignedEmployeeCount, participants, participants,
                    currentOccupants, action, history, bookingOptions);
        }).toList();
        return new AssignmentsResponse(round.id, round.status.name(), round.processedAt, round.capacitySource,
                round.floorPlanVersion == null ? null : round.floorPlanVersion.id, round.seatCapacity,
                new AssignmentsResponse.RelinquishmentCredits(creditTokens, creditExpiry), days);
    }

    private static Instant targetDateEnd(LocalDate date, String zone) {
        return date.plusDays(1).atStartOfDay(ZoneId.of(zone)).toInstant();
    }

    private static String statusText(OfferCandidateStatus status) {
        return switch (status) {
            case PENDING -> "Pending";
            case CLAIMED -> "Claimed";
            case DECLINED -> "Declined";
            case AUTO_DECLINED_INSUFFICIENT_FUNDS -> "Insufficient tokens";
            case CLAIMED_BY_OTHER -> "Claimed by another eligible employee";
            case EXPIRED -> "Offer expired";
        };
    }

}