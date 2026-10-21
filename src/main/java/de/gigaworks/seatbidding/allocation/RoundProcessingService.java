package de.gigaworks.seatbidding.allocation;

import de.gigaworks.seatbidding.persistence.BidEntity;
import de.gigaworks.seatbidding.persistence.AllocationUnitEntity;
import de.gigaworks.seatbidding.persistence.AllocationUnitRepository;
import de.gigaworks.seatbidding.persistence.BidRepository;
import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.persistence.LedgerType;
import de.gigaworks.seatbidding.persistence.RoundDateRepository;
import de.gigaworks.seatbidding.persistence.RoundAllocationAuditEntity;
import de.gigaworks.seatbidding.persistence.RoundAllocationAuditRepository;
import de.gigaworks.seatbidding.persistence.RoundParticipationEntity;
import de.gigaworks.seatbidding.persistence.RoundParticipationRepository;
import de.gigaworks.seatbidding.persistence.RoundStatus;
import de.gigaworks.seatbidding.persistence.SeatAssignmentEntity;
import de.gigaworks.seatbidding.persistence.SeatAssignmentRepository;
import de.gigaworks.seatbidding.persistence.SeatReservationRepository;
import de.gigaworks.seatbidding.persistence.TokenLedgerEntity;
import de.gigaworks.seatbidding.persistence.TokenLedgerRepository;
import de.gigaworks.seatbidding.persistence.EmployeeSeatPreferenceRepository;
import de.gigaworks.seatbidding.persistence.EmployeeSeatPreferenceRevisionRepository;
import de.gigaworks.seatbidding.persistence.FloorPlanVersionSeatRepository;
import de.gigaworks.seatbidding.persistence.GuestSeatPreferenceRepository;
import de.gigaworks.seatbidding.persistence.GuestSeatPreferenceRevisionRepository;
import de.gigaworks.seatbidding.persistence.PhysicalSeatEntity;
import de.gigaworks.seatbidding.persistence.ReservedGuestSeatAssignmentEntity;
import de.gigaworks.seatbidding.persistence.ReservedGuestSeatAssignmentRepository;
import de.gigaworks.seatbidding.round.RoundFactory;
import de.gigaworks.seatbidding.tokens.BalanceCalculator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class RoundProcessingService {

    @Inject
    BiddingRoundRepository rounds;

    @Inject
    RoundDateRepository dates;

    @Inject
    RoundParticipationRepository participations;

    @Inject
    BidRepository bids;

    @Inject
    SeatAssignmentRepository assignments;

    @Inject
    AllocationUnitRepository allocationUnits;

    @Inject
    SeatReservationRepository reservations;

    @Inject
    RoundAllocationAuditRepository allocationAudits;

    @Inject
    TokenLedgerRepository ledger;

    @Inject
    AllocationEngine allocation;

    @Inject
    RoundFactory roundFactory;

    @Inject
    FloorPlanVersionSeatRepository floorPlanSeats;

    @Inject
    EmployeeSeatPreferenceRevisionRepository preferenceRevisions;

    @Inject
    EmployeeSeatPreferenceRepository preferences;

    @Inject
    GuestSeatPreferenceRevisionRepository guestPreferenceRevisions;

    @Inject
    GuestSeatPreferenceRepository guestPreferences;

    @Inject
    ReservedGuestSeatAssignmentRepository guestAssignments;

    @Inject
    SeatPlacementOptimizer seatPlacementOptimizer;

    @Inject
    Clock clock;

    @Transactional
    public boolean processDueRound() {
        var now = clock.instant();
        var round = rounds.findDueForUpdate(now).orElse(null);
        if (round == null) {
            return false;
        }
        if (round.status != RoundStatus.OPEN || now.isBefore(round.cutoffAt)) {
            return false;
        }

        round.status = RoundStatus.PROCESSING;
        round.processingStartedAt = now;
        var roundDates = dates.findForRound(round.id);
        var reservationByDate = reservations.findForDatesForUpdate(
                roundDates.stream().map(date -> date.targetDate).toList()).stream()
                .collect(Collectors.toMap(reservation -> reservation.targetDate, Function.identity()));
        var roundBids = bids.findForRound(round.id);
        var datesById = roundDates.stream().collect(Collectors.toMap(date -> date.id, Function.identity()));
        var bidsById = roundBids.stream().collect(Collectors.toMap(bid -> bid.id, Function.identity()));
        var solution = allocation.allocate(round.id, round.seatCapacity,
                roundDates.stream().map(date -> {
                    var reservation = reservationByDate.get(date.targetDate);
                    int reserved = reservation == null ? 0 : reservation.reservedSeatCount;
                    if (reserved > round.seatCapacity) {
                        throw new IllegalStateException("reservation exceeds the round's physical seat capacity");
                    }
                    return new RoundAllocation.TargetDate(date.id, date.targetDate,
                            reservation == null ? null : reservation.id, reserved, round.seatCapacity - reserved);
                }).toList(),
                roundBids.stream().map(bid -> new RoundAllocation.Bid(bid.roundDate.id, bid.roundDate.targetDate,
                        bid.id, bid.participation.employee.id, bid.tokens, bid.attendancePeriod)).toList());

        var placement = placeSeats(round, solution, datesById, reservationByDate);

        for (var result : solution.results()) {
            var unit = new AllocationUnitEntity();
            unit.roundDate = datesById.get(result.dateId());
            unit.unitType = result.unitType();
            unit.scoreTokens = result.scoreTokens();
            unit.fairnessIdentity = result.fairnessIdentity();
            unit.assigned = result.assigned();
            unit.scoreRank = result.scoreRank();
            unit.finalRank = result.finalRank();
            unit.resolution = result.resolution();
            unit.boundaryTieGroup = result.boundaryTieGroup();
            unit.physicalSeat = placement.byUnit().get(result.dateId() + "|" + result.fairnessIdentity());
            allocationUnits.persist(unit);
            for (var member : result.members()) {
                var assignment = new SeatAssignmentEntity();
                assignment.roundDate = unit.roundDate;
                assignment.bid = bidsById.get(member.bidId());
                assignment.allocationUnit = unit;
                assignment.assigned = result.assigned();
                assignment.attendancePeriod = member.attendancePeriod();
                assignment.unitMemberOrder = member.memberOrder();
                assignment.displayRank = member.displayRank();
                // Retained deployed columns remain truthful compatibility copies for member rows.
                assignment.tokenRank = result.scoreRank();
                assignment.finalRank = member.displayRank();
                assignment.resolution = result.resolution();
                assignment.boundaryTieGroup = result.boundaryTieGroup();
                assignment.tieGroup = null;
                assignment.drawValue = null;
                assignment.algorithmVersion = round.floorPlanVersion == null ? result.algorithmVersion()
                        : "v6-floor-plan-token-guests";
                assignments.persist(assignment);
            }
        }
        persistAudit(round, solution, placement);
        assignments.flush();

        var spendByParticipation = new HashMap<Long, Integer>();
        for (var assignment : assignments.findAssignedForRound(round.id)) {
            BidEntity bid = assignment.bid;
            spendByParticipation.merge(bid.participation.id, bid.tokens, Integer::sum);
            addSpendLedger(bid, now);
        }

        Map<Long, Integer> carryByEmployee = new HashMap<>();
        for (RoundParticipationEntity participation : participations.findForRound(round.id)) {
            int spend = spendByParticipation.getOrDefault(participation.id, 0);
            var closing = BalanceCalculator.close(participation.startingBalance, spend, round.carryOverCap);
            participation.successfulBidTokens = spend;
            participation.remainingBalance = closing.remaining();
            participation.carriedOutTokens = closing.carriedOut();
            carryByEmployee.put(participation.employee.id, closing.carriedOut());
            if (closing.expired() > 0) {
                addExpiryLedger(participation, closing.expired(), now);
            }
        }

        round.status = RoundStatus.COMPLETED;
        round.processedAt = now;
        rounds.flush();
        roundFactory.create(round.sequenceNo + 1, now, round.cutoffAt, round, carryByEmployee);
        return true;
    }

    private void persistAudit(de.gigaworks.seatbidding.persistence.BiddingRoundEntity round,
            RoundAllocation.Solution solution, PlacementAudit placement) {
        var audit = new RoundAllocationAuditEntity();
        audit.round = round;
        audit.algorithmVersion = round.floorPlanVersion == null ? RoundAllocation.ALGORITHM_VERSION
                : "v6-floor-plan-token-guests";
        audit.inputFingerprint = solution.inputFingerprint();
        audit.objectiveSummary = objectiveSummary(solution.objective());
        audit.pairingAudit = solution.pairingAudit();
        audit.selectedSolutionFingerprint = solution.selectedSolutionFingerprint();
        audit.randomSelectionValue = null;
        audit.capacitySelectionValue = solution.capacitySelectionValue();
        audit.seatPlacementInputFingerprint = placement.inputFingerprint();
        audit.seatPlacementObjectiveSummary = placement.objectiveSummary();
        audit.seatPlacementAudit = placement.auditJson();
        audit.seatPlacementSelectionValue = placement.selectionValue();
        allocationAudits.persist(audit);
    }

    private PlacementAudit placeSeats(de.gigaworks.seatbidding.persistence.BiddingRoundEntity round,
            RoundAllocation.Solution solution,
            Map<Long, de.gigaworks.seatbidding.persistence.RoundDateEntity> datesById,
            Map<java.time.LocalDate, de.gigaworks.seatbidding.persistence.SeatReservationEntity> reservationByDate) {
        if (round.floorPlanVersion == null) {
            return PlacementAudit.empty();
        }
        var seatEntities = floorPlanSeats.findActiveForVersion(round.floorPlanVersion.id).stream()
                .map(value -> value.physicalSeat).toList();
        var activeSeatIds = seatEntities.stream().map(value -> value.id).collect(Collectors.toSet());
        var employeeIds = solution.results().stream().filter(RoundAllocation.Result::assigned)
                .flatMap(value -> value.members().stream()).map(RoundAllocation.MemberResult::employeeId)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, Map<Long, Integer>> ratings = new HashMap<>();
        Map<Long, Long> revisions = new HashMap<>();
        for (Long employeeId : employeeIds) {
            preferenceRevisions.findAtCutoff(employeeId, round.floorPlanVersion.floorPlan.id, round.cutoffAt)
                    .ifPresent(revision -> {
                        revisions.put(employeeId, revision.revisionNo);
                        var effective = preferences.findForRevision(revision.id).stream()
                                .filter(value -> activeSeatIds.contains(value.physicalSeat.id)).collect(
                                Collectors.toMap(value -> value.physicalSeat.id, value -> (int) value.stars));
                        if (!effective.isEmpty()) {
                            ratings.put(employeeId, effective);
                        }
                    });
        }
        var guestRevision = guestPreferenceRevisions.findAtCutoff(round.floorPlanVersion.floorPlan.id, round.cutoffAt)
                .orElse(null);
        Map<Long, Integer> guestRatings = guestRevision == null ? Map.of()
                : guestPreferences.findForRevision(guestRevision.id).stream()
                        .filter(value -> activeSeatIds.contains(value.physicalSeat.id)).collect(
                        Collectors.toMap(value -> value.physicalSeat.id, value -> (int) value.stars));
        Map<String, PhysicalSeatEntity> byUnit = new HashMap<>();
        var selectionValues = new java.util.ArrayList<String>();
        var dateAudits = new java.util.ArrayList<String>();
        for (var date : datesById.keySet().stream().sorted().toList()) {
            var successful = solution.results().stream().filter(value -> value.dateId() == date && value.assigned())
                    .toList();
            var result = seatPlacementOptimizer.place(successful, seatEntities, ratings);
            result.byFairnessIdentity().forEach((identity, seat) -> byUnit.put(date + "|" + identity, seat));
            if (result.mappingSelectionValue() != null) {
                selectionValues.add(date + ":" + result.mappingSelectionValue());
            }
            var used = new java.util.HashSet<>(result.byFairnessIdentity().values());
            var roundDate = datesById.get(date);
            var reservation = reservationByDate.get(roundDate.targetDate);
            var selectedGuests = new java.util.ArrayList<PhysicalSeatEntity>();
            if (reservation != null) {
                for (var seat : GuestSeatSelector.select(seatEntities, used, guestRatings,
                        reservation.reservedSeatCount)) {
                    var assignment = new ReservedGuestSeatAssignmentEntity();
                    assignment.roundDate = roundDate;
                    assignment.reservation = reservation;
                    assignment.physicalSeat = seat;
                    guestAssignments.persist(assignment);
                    selectedGuests.add(seat);
                }
            }
            String priority = result.priorityOrder().stream().map(value -> "\"" + value + "\"")
                    .collect(Collectors.joining(","));
            String satisfaction = result.satisfactionKeys().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(value -> "\"" + value.getKey() + "\":\"" + value.getValue() + "\"")
                    .collect(Collectors.joining(","));
            String guestSeats = selectedGuests.stream().map(value -> Long.toString(value.id))
                    .collect(Collectors.joining(","));
            dateAudits.add("{\"dateId\":" + date + ",\"priorityOrder\":[" + priority
                    + "],\"prioritySelection\":\"" + result.prioritySelectionValue()
                    + "\",\"satisfactionKeys\":{" + satisfaction + "},\"mappingSelection\":"
                    + (result.mappingSelectionValue() == null ? "null" : "\"" + result.mappingSelectionValue() + "\"")
                    + ",\"guestSeats\":[" + guestSeats + "]}");
        }
        guestAssignments.flush();
        String canonical = byUnit.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(value -> value.getKey() + "=" + value.getValue().id).collect(Collectors.joining(","));
        String ratingInput = ratings.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(employee -> employee.getKey() + ":" + employee.getValue().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey()).map(value -> value.getKey() + "=" + value.getValue())
                        .collect(Collectors.joining(";"))).collect(Collectors.joining(","));
        String seatInput = seatEntities.stream().sorted(java.util.Comparator.comparingInt(value -> value.seatNumber))
                .map(value -> value.id + "=" + value.seatNumber).collect(Collectors.joining(","));
        String guestRatingInput = guestRatings.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(value -> value.getKey() + "=" + value.getValue()).collect(Collectors.joining(";"));
        String input = "version=" + round.floorPlanVersion.id + "|seats=" + seatInput + "|ratings=" + ratingInput
                + "|guestRevision=" + (guestRevision == null ? 0 : guestRevision.revisionNo)
                + "|guestRatings=" + guestRatingInput;
        String revisionsJson = revisions.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(value -> "\"" + value.getKey() + "\":" + value.getValue()).collect(Collectors.joining(","));
        return new PlacementAudit(Map.copyOf(byUnit), AllocationFingerprints.seatPlacement(input),
                "{\"objective\":\"strict-token-priority-lexicographic-satisfaction\",\"dates\":["
                        + String.join(",", dateAudits) + "]}",
                "{\"version\":\"v2\",\"preferenceRevisions\":{" + revisionsJson
                        + "},\"guestPreferenceRevision\":" + (guestRevision == null ? "null" : guestRevision.revisionNo)
                        + ",\"mapping\":\"" + canonical + "\",\"ratings\":\"" + ratingInput
                        + "\",\"guestRatings\":\"" + guestRatingInput + "\",\"dates\":["
                        + String.join(",", dateAudits) + "]}",
                String.join(",", selectionValues));
    }

    private record PlacementAudit(Map<String, PhysicalSeatEntity> byUnit, String inputFingerprint,
            String objectiveSummary, String auditJson, String selectionValue) {

        static PlacementAudit empty() {
            return new PlacementAudit(Map.of(), null, null, null, null);
        }
    }

    private static String objectiveSummary(RoundAllocation.Objective objective) {
        String vector = objective.sortedTieWinCounts().stream().map(String::valueOf).collect(Collectors.joining(","));
        return "{\"filledUnresolvedSlots\":" + objective.filledUnresolvedSlots()
                + ",\"distinctTieWinners\":" + objective.distinctTieWinners()
                + ",\"sortedTieWinCounts\":[" + vector + "]}";
    }

    private void addSpendLedger(BidEntity bid, java.time.Instant now) {
        var entry = new TokenLedgerEntity();
        entry.employee = bid.participation.employee;
        entry.round = bid.participation.round;
        entry.bid = bid;
        entry.type = LedgerType.BID_SPEND;
        entry.amount = -bid.tokens;
        entry.idempotencyKey = "bid:" + bid.id + ":spend";
        entry.occurredAt = now;
        ledger.persist(entry);
    }

    private void addExpiryLedger(RoundParticipationEntity participation, int expired, java.time.Instant now) {
        var entry = new TokenLedgerEntity();
        entry.employee = participation.employee;
        entry.round = participation.round;
        entry.type = LedgerType.EXPIRY;
        entry.amount = -expired;
        entry.idempotencyKey = "round:" + participation.round.id + ":employee:" + participation.employee.id + ":expiry";
        entry.occurredAt = now;
        ledger.persist(entry);
    }

}