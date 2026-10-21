package de.gigaworks.seatbidding.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.gigaworks.seatbidding.persistence.AllocationUnitRepository;
import de.gigaworks.seatbidding.persistence.AttendancePeriod;
import de.gigaworks.seatbidding.persistence.BidEntity;
import de.gigaworks.seatbidding.persistence.BidRepository;
import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.persistence.CapacitySource;
import de.gigaworks.seatbidding.persistence.EmployeeEntity;
import de.gigaworks.seatbidding.persistence.EmployeeRepository;
import de.gigaworks.seatbidding.persistence.EmployeeSeatPreferenceEntity;
import de.gigaworks.seatbidding.persistence.EmployeeSeatPreferenceRepository;
import de.gigaworks.seatbidding.persistence.EmployeeSeatPreferenceRevisionEntity;
import de.gigaworks.seatbidding.persistence.EmployeeSeatPreferenceRevisionRepository;
import de.gigaworks.seatbidding.persistence.FloorPlanEntity;
import de.gigaworks.seatbidding.persistence.FloorPlanRepository;
import de.gigaworks.seatbidding.persistence.FloorPlanSeatState;
import de.gigaworks.seatbidding.persistence.FloorPlanVersionEntity;
import de.gigaworks.seatbidding.persistence.FloorPlanVersionRepository;
import de.gigaworks.seatbidding.persistence.FloorPlanVersionSeatEntity;
import de.gigaworks.seatbidding.persistence.FloorPlanVersionSeatRepository;
import de.gigaworks.seatbidding.persistence.FloorPlanVersionStatus;
import de.gigaworks.seatbidding.persistence.GuestSeatPreferenceEntity;
import de.gigaworks.seatbidding.persistence.GuestSeatPreferenceRepository;
import de.gigaworks.seatbidding.persistence.GuestSeatPreferenceRevisionEntity;
import de.gigaworks.seatbidding.persistence.GuestSeatPreferenceRevisionRepository;
import de.gigaworks.seatbidding.persistence.PhysicalSeatEntity;
import de.gigaworks.seatbidding.persistence.PhysicalSeatRepository;
import de.gigaworks.seatbidding.persistence.ReservedGuestSeatAssignmentRepository;
import de.gigaworks.seatbidding.persistence.RoundAllocationAuditRepository;
import de.gigaworks.seatbidding.persistence.RoundDateRepository;
import de.gigaworks.seatbidding.persistence.RoundParticipationRepository;
import de.gigaworks.seatbidding.persistence.SeatReservationEntity;
import de.gigaworks.seatbidding.persistence.SeatReservationRepository;
import de.gigaworks.seatbidding.round.RoundFactory;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = de.gigaworks.seatbidding.support.PostgresTestResource.class,
        restrictToAnnotatedClass = true)
class FloorPlanGuestPlacementPersistenceTest {

    @Inject BiddingRoundRepository rounds;
    @Inject RoundDateRepository dates;
    @Inject EmployeeRepository employees;
    @Inject RoundParticipationRepository participations;
    @Inject BidRepository bids;
    @Inject FloorPlanRepository plans;
    @Inject FloorPlanVersionRepository versions;
    @Inject PhysicalSeatRepository physicalSeats;
    @Inject FloorPlanVersionSeatRepository markers;
    @Inject EmployeeSeatPreferenceRevisionRepository preferenceRevisions;
    @Inject EmployeeSeatPreferenceRepository preferences;
    @Inject GuestSeatPreferenceRevisionRepository guestPreferenceRevisions;
    @Inject GuestSeatPreferenceRepository guestPreferences;
    @Inject SeatReservationRepository reservations;
    @Inject ReservedGuestSeatAssignmentRepository guestAssignments;
    @Inject AllocationUnitRepository units;
    @Inject RoundAllocationAuditRepository audits;
    @Inject RoundFactory roundFactory;
    @Inject RoundProcessingService processing;

    @Test
    void employeesArePlacedFirstAndGuestRatingSelectsFromOnlyRemainingSeats() {
        QuarkusTransaction.requiringNew().run(() -> {
            var round = rounds.findOpen().orElseThrow();
            round.seatCapacity = 4;
            round.biddingOpensAt = Instant.now().minusSeconds(604800);
            round.cutoffAt = Instant.now().minusSeconds(1);
            var day = dates.findForRound(round.id).getFirst();

            var employeeBySeat = new HashMap<Integer, EmployeeEntity>();
            for (int number = 1; number <= 3; number++) {
                var employee = employee("floor-plan-employee-" + number + "@example.com");
                employeeBySeat.put(number, employee);
                createBid(round, day, employee, 40 - number * 10);
            }
            var admin = employeeBySeat.get(1);
            admin.admin = true;
            var plan = new FloorPlanEntity();
            plan.permanentNumberWatermark = 4;
            plans.persistAndFlush(plan);
            var version = version(plan, admin);
            versions.persistAndFlush(version);
            plan.activeVersion = version;
            round.capacitySource = CapacitySource.FLOOR_PLAN;
            round.floorPlanVersion = version;

            Map<Integer, PhysicalSeatEntity> seats = new HashMap<>();
            for (int number = 1; number <= 4; number++) {
                var seat = new PhysicalSeatEntity();
                seat.floorPlan = plan;
                seat.seatNumber = number;
                seat.firstPublishedVersion = version;
                physicalSeats.persistAndFlush(seat);
                seats.put(number, seat);
                var marker = new FloorPlanVersionSeatEntity();
                marker.version = version;
                marker.physicalSeat = seat;
                marker.seatNumber = number;
                marker.normalizedX = BigDecimal.valueOf(number, 1);
                marker.normalizedY = BigDecimal.valueOf(0.5);
                marker.state = FloorPlanSeatState.ACTIVE;
                markers.persist(marker);
            }
            for (int number = 1; number <= 3; number++) {
                var revision = new EmployeeSeatPreferenceRevisionEntity();
                revision.employee = employeeBySeat.get(number);
                revision.floorPlan = plan;
                revision.revisionNo = 1;
                revision.createdAt = round.cutoffAt.minusSeconds(10);
                preferenceRevisions.persistAndFlush(revision);
                var preference = new EmployeeSeatPreferenceEntity();
                preference.revision = revision;
                preference.physicalSeat = seats.get(number);
                preference.stars = 3;
                preferences.persist(preference);
            }
            var guestRevision = new GuestSeatPreferenceRevisionEntity();
            guestRevision.floorPlan = plan;
            guestRevision.revisionNo = 1;
            guestRevision.createdBy = admin;
            guestRevision.createdAt = round.cutoffAt.minusSeconds(10);
            guestPreferenceRevisions.persistAndFlush(guestRevision);
            var guestPreference = new GuestSeatPreferenceEntity();
            guestPreference.revision = guestRevision;
            guestPreference.physicalSeat = seats.get(4);
            guestPreference.stars = 3;
            guestPreferences.persist(guestPreference);

            var reservation = new SeatReservationEntity();
            reservation.targetDate = day.targetDate;
            reservation.reservedSeatCount = 1;
            reservation.createdBy = admin;
            reservations.persist(reservation);
            var emptyDateReservation = new SeatReservationEntity();
            emptyDateReservation.targetDate = dates.findForRound(round.id).get(1).targetDate;
            emptyDateReservation.reservedSeatCount = 1;
            emptyDateReservation.createdBy = admin;
            reservations.persist(emptyDateReservation);
        });

        assertTrue(processing.processDueRound());
        QuarkusTransaction.requiringNew().run(() -> {
            var completed = rounds.findLatestCompleted().orElseThrow();
            var employeeSeatNumbers = units.find("roundDate.round.id = ?1 and assigned = true", completed.id)
                    .stream().map(unit -> unit.physicalSeat.seatNumber).sorted().toList();
            assertEquals(java.util.List.of(1, 2, 3), employeeSeatNumbers);
            var guests = guestAssignments.findForRound(completed.id);
            assertEquals(2, guests.size());
            assertEquals(java.util.List.of(4, 4), guests.stream().map(value -> value.physicalSeat.seatNumber).toList());
            assertEquals("v6-floor-plan-token-guests", audits.findForRound(completed.id).orElseThrow().algorithmVersion);
            assertTrue(audits.findForRound(completed.id).orElseThrow().seatPlacementAudit.contains("guestSeats"));
        });
        assertThrows(RuntimeException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            var completed = rounds.findLatestCompleted().orElseThrow();
            var employeeUnit = units.find("roundDate.round.id = ?1 and assigned = true", completed.id)
                    .firstResult();
            var existingGuest = guestAssignments.findForRound(completed.id).getFirst();
            var duplicate = new de.gigaworks.seatbidding.persistence.ReservedGuestSeatAssignmentEntity();
            duplicate.roundDate = employeeUnit.roundDate;
            duplicate.reservation = existingGuest.reservation;
            duplicate.physicalSeat = employeeUnit.physicalSeat;
            guestAssignments.persistAndFlush(duplicate);
        }));
    }

    private EmployeeEntity employee(String email) {
        var employee = new EmployeeEntity();
        employee.email = email;
        employee.firstName = "Test";
        employee.lastName = "Employee";
        employee.enabled = true;
        employees.persistAndFlush(employee);
        return employee;
    }

    private void createBid(de.gigaworks.seatbidding.persistence.BiddingRoundEntity round,
            de.gigaworks.seatbidding.persistence.RoundDateEntity day, EmployeeEntity employee, int tokens) {
        var participation = roundFactory.createParticipation(round, employee, 0, Instant.now());
        participations.flush();
        var bid = new BidEntity();
        bid.roundDate = day;
        bid.participation = participation;
        bid.tokens = tokens;
        bid.attendancePeriod = AttendancePeriod.FULL_DAY;
        bids.persist(bid);
    }

    private static FloorPlanVersionEntity version(FloorPlanEntity plan, EmployeeEntity creator) {
        var version = new FloorPlanVersionEntity();
        version.floorPlan = plan;
        version.versionNo = 1;
        version.status = FloorPlanVersionStatus.PUBLISHED;
        version.sanitizedSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"/>";
        version.contentSha256 = "a".repeat(64);
        version.viewBoxMinX = BigDecimal.ZERO;
        version.viewBoxMinY = BigDecimal.ZERO;
        version.viewBoxWidth = BigDecimal.valueOf(100);
        version.viewBoxHeight = BigDecimal.valueOf(100);
        version.publishedAt = Instant.now();
        version.createdBy = creator;
        return version;
    }
}