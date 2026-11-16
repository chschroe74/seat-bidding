package de.gigaworks.seatbidding.occupancy;

import de.gigaworks.seatbidding.auth.EmployeeIdentityService;
import de.gigaworks.seatbidding.dto.SeatAvailabilityResponse;
import de.gigaworks.seatbidding.exception.ApplicationProblem;
import de.gigaworks.seatbidding.notification.SeatOfferNotificationRequested;
import de.gigaworks.seatbidding.persistence.AssignmentRelinquishmentEntity;
import de.gigaworks.seatbidding.persistence.AssignmentRelinquishmentRepository;
import de.gigaworks.seatbidding.persistence.AttendancePeriod;
import de.gigaworks.seatbidding.persistence.BidRepository;
import de.gigaworks.seatbidding.persistence.BiddingRoundEntity;
import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.persistence.CapacitySource;
import de.gigaworks.seatbidding.persistence.EmployeeEntity;
import de.gigaworks.seatbidding.persistence.EmployeeNotificationSettingsRepository;
import de.gigaworks.seatbidding.persistence.FloorPlanVersionSeatRepository;
import de.gigaworks.seatbidding.persistence.LedgerType;
import de.gigaworks.seatbidding.persistence.OccupancySourceType;
import de.gigaworks.seatbidding.persistence.OccupancyStatus;
import de.gigaworks.seatbidding.persistence.OfferCandidateStatus;
import de.gigaworks.seatbidding.persistence.OfferStatus;
import de.gigaworks.seatbidding.persistence.PhysicalSeatEntity;
import de.gigaworks.seatbidding.persistence.PostAllocationBookingCreditEntity;
import de.gigaworks.seatbidding.persistence.PostAllocationBookingCreditRepository;
import de.gigaworks.seatbidding.persistence.PostAllocationBookingEntity;
import de.gigaworks.seatbidding.persistence.PostAllocationBookingRepository;
import de.gigaworks.seatbidding.persistence.PostAllocationBookingType;
import de.gigaworks.seatbidding.persistence.ReleasedCapacityOfferEntity;
import de.gigaworks.seatbidding.persistence.ReleasedCapacityOfferRepository;
import de.gigaworks.seatbidding.persistence.RelinquishmentCreditEntity;
import de.gigaworks.seatbidding.persistence.RelinquishmentCreditRepository;
import de.gigaworks.seatbidding.persistence.ReservedGuestSeatAssignmentRepository;
import de.gigaworks.seatbidding.persistence.RoundDateCapacityStateEntity;
import de.gigaworks.seatbidding.persistence.RoundDateCapacityStateRepository;
import de.gigaworks.seatbidding.persistence.RoundDateEntity;
import de.gigaworks.seatbidding.persistence.RoundDateRepository;
import de.gigaworks.seatbidding.persistence.RoundParticipationEntity;
import de.gigaworks.seatbidding.persistence.RoundParticipationRepository;
import de.gigaworks.seatbidding.persistence.RoundStatus;
import de.gigaworks.seatbidding.persistence.SeatAssignmentEntity;
import de.gigaworks.seatbidding.persistence.SeatAssignmentRepository;
import de.gigaworks.seatbidding.persistence.SeatOccupancyEntity;
import de.gigaworks.seatbidding.persistence.SeatOccupancyRepository;
import de.gigaworks.seatbidding.persistence.SeatOfferCandidateEntity;
import de.gigaworks.seatbidding.persistence.SeatOfferCandidateRepository;
import de.gigaworks.seatbidding.persistence.SeatOfferNotificationDispatchEntity;
import de.gigaworks.seatbidding.persistence.SeatOfferNotificationDispatchRepository;
import de.gigaworks.seatbidding.persistence.SeatReservationRepository;
import de.gigaworks.seatbidding.persistence.TokenLedgerEntity;
import de.gigaworks.seatbidding.persistence.TokenLedgerRepository;
import de.gigaworks.seatbidding.round.SeatBiddingConfiguration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class PostAllocationService {

    @Inject EmployeeIdentityService identity;
    @Inject BiddingRoundRepository rounds;
    @Inject RoundDateRepository dates;
    @Inject RoundDateCapacityStateRepository capacityStates;
    @Inject SeatOccupancyRepository occupancies;
    @Inject AssignmentRelinquishmentRepository relinquishments;
    @Inject RelinquishmentCreditRepository credits;
    @Inject ReleasedCapacityOfferRepository offers;
    @Inject SeatOfferCandidateRepository candidates;
    @Inject PostAllocationBookingRepository bookings;
    @Inject PostAllocationBookingCreditRepository bookingCredits;
    @Inject RoundParticipationRepository participations;
    @Inject BidRepository bids;
    @Inject SeatAssignmentRepository assignments;
    @Inject SeatReservationRepository reservations;
    @Inject FloorPlanVersionSeatRepository versionSeats;
    @Inject ReservedGuestSeatAssignmentRepository guestSeats;
    @Inject TokenLedgerRepository ledger;
    @Inject EmployeeNotificationSettingsRepository notificationSettings;
    @Inject SeatOfferNotificationDispatchRepository offerDispatches;
    @Inject Event<SeatOfferNotificationRequested> notificationEvents;
    @Inject SeatBiddingConfiguration configuration;
    @Inject Clock clock;

    @Transactional
    public void relinquish(long roundId, LocalDate targetDate, long occupancyId) {
        var employee = identity.resolve();
        var context = lockContext(roundId, targetDate);
        var now = clock.instant();
        requireBeforeEnd(context.date(), now);
        var occupancy = occupancies.findOwnedActiveForUpdate(occupancyId, employee.id, context.date().id)
                .orElseThrow(() -> ApplicationProblem.notFound("OCCUPANCY_NOT_FOUND",
                        "No active occupancy belonging to the current employee was found."));

        PostAllocationBookingEntity booking = null;
        if (occupancy.sourceType != OccupancySourceType.ORIGINAL_ASSIGNMENT) {
            booking = bookings.findForOccupancyForUpdate(occupancy.id).orElseThrow(() ->
                    new IllegalStateException("Later occupancy has no funding record"));
            requireRefundable(booking, now);
        }

        occupancy.status = OccupancyStatus.RELINQUISHED;
        occupancy.relinquishedAt = now;
        release(context.state(), occupancy.attendancePeriod);

        var release = new AssignmentRelinquishmentEntity();
        release.occupancy = occupancy;
        release.employee = employee;
        release.roundDate = context.date();
        release.attendancePeriod = occupancy.attendancePeriod;
        release.releasedPhysicalSeat = occupancy.physicalSeat;
        release.releasedSeatNumber = occupancy.physicalSeat == null ? null : occupancy.physicalSeat.seatNumber;
        release.releasedAt = now;
        release.offerDeadline = min(now.plus(Duration.ofHours(24)), targetDateEnd(context.date()));
        release.originalAssignment = occupancy.sourceType == OccupancySourceType.ORIGINAL_ASSIGNMENT;
        relinquishments.persist(release);

        if (release.originalAssignment) {
            createCredit(occupancy, context.round(), employee, now);
        }
        else {
            refundBooking(booking, employee, now);
        }

        var offer = new ReleasedCapacityOfferEntity();
        offer.relinquishment = release;
        offer.roundDate = context.date();
        offer.attendancePeriod = occupancy.attendancePeriod;
        offer.initialMorningCapacity = occupancy.attendancePeriod == AttendancePeriod.AFTERNOON_ONLY ? 0 : 1;
        offer.initialAfternoonCapacity = occupancy.attendancePeriod == AttendancePeriod.MORNING_ONLY ? 0 : 1;
        offer.deadline = release.offerDeadline;
        offer.status = OfferStatus.ACTIVE;
        offers.persist(offer);
        offers.flush();
        activateNextTier(offer, employee.id, now, context);
    }

    @Transactional
    public ClaimResult claim(long roundId, LocalDate targetDate, long offerId, long candidateId, Long physicalSeatId) {
        var employee = identity.resolve();
        var context = lockContext(roundId, targetDate);
        var now = clock.instant();
        requireBeforeEnd(context.date(), now);
        expireOffers(context, now);
        var candidate = candidates.findOwnedForUpdate(offerId, candidateId, employee.id)
                .orElseThrow(() -> ApplicationProblem.notFound("OFFER_CANDIDATE_NOT_FOUND",
                        "No offer candidate belonging to the current employee was found."));
        if (candidate.status != OfferCandidateStatus.PENDING || !now.isBefore(candidate.offer.deadline)) {
            if (candidate.status == OfferCandidateStatus.EXPIRED || !now.isBefore(candidate.offer.deadline)) {
                return ClaimResult.EXPIRED;
            }
            throw conflict("OFFER_NOT_PENDING", "This offer is no longer pending.");
        }
        if (occupancies.hasActive(context.date().id, employee.id)) {
            throw conflict("ALREADY_OCCUPIES_DATE", "The employee already occupies this date.");
        }
        if (occupancies.hasRelinquishedLaterBooking(context.date().id, employee.id)) {
            throw conflict("LATER_BOOKING_RELINQUISHED",
                    "A relinquished later booking cannot be replaced for the same date.");
        }
        if (!hasCapacity(context, candidate.attendancePeriod)) {
            candidate.status = OfferCandidateStatus.CLAIMED_BY_OTHER;
            candidate.respondedAt = now;
            reconcileAfterCapacityChange(context, now);
            return ClaimResult.CAPACITY_TAKEN;
        }
        var selectedSeat = validateSelectedSeat(context, candidate.attendancePeriod, physicalSeatId);
        Funding funding;
        try {
            funding = funding(context.round(), employee, candidate.requiredTokens, now);
        }
        catch (ApplicationProblem problem) {
            if (!Set.of("INSUFFICIENT_POST_ALLOCATION_FUNDS", "PARTICIPATION_NOT_FOUND", "OPEN_ROUND_NOT_FOUND")
                    .contains(problem.code())) {
                throw problem;
            }
            candidate.status = OfferCandidateStatus.AUTO_DECLINED_INSUFFICIENT_FUNDS;
            candidate.respondedAt = now;
            progressOffer(candidate.offer, now, context);
            return ClaimResult.INSUFFICIENT_FUNDS;
        }
        var occupancy = createOccupancy(context, employee, candidate.attendancePeriod, selectedSeat,
                OccupancySourceType.WAITLIST_CLAIM, null, now);
        var booking = createBooking(occupancy, PostAllocationBookingType.WAITLIST_CLAIM, candidate,
                candidate.requiredTokens, funding, now);
        applyFunding(context.round(), employee, booking, funding, now);
        candidate.status = OfferCandidateStatus.CLAIMED;
        candidate.respondedAt = now;
        reconcileAfterCapacityChange(context, now);
        return ClaimResult.CLAIMED;
    }

    @Transactional
    public void decline(long roundId, LocalDate targetDate, long offerId, long candidateId) {
        var employee = identity.resolve();
        var context = lockContext(roundId, targetDate);
        var now = clock.instant();
        expireOffers(context, now);
        var candidate = candidates.findOwnedForUpdate(offerId, candidateId, employee.id)
                .orElseThrow(() -> ApplicationProblem.notFound("OFFER_CANDIDATE_NOT_FOUND",
                        "No offer candidate belonging to the current employee was found."));
        if (candidate.status == OfferCandidateStatus.DECLINED
                || candidate.status == OfferCandidateStatus.EXPIRED) {
            return;
        }
        if (candidate.status != OfferCandidateStatus.PENDING) {
            throw conflict("OFFER_NOT_PENDING", "This offer already has a terminal outcome.");
        }
        candidate.status = OfferCandidateStatus.DECLINED;
        candidate.respondedAt = now;
        progressOffer(candidate.offer, now, context);
    }

    @Transactional
    public long book(long roundId, LocalDate targetDate, AttendancePeriod period, Long physicalSeatId) {
        var employee = identity.resolve();
        var context = lockContext(roundId, targetDate);
        var now = clock.instant();
        requireBeforeEnd(context.date(), now);
        expireOffers(context, now);
        if (occupancies.hasActive(context.date().id, employee.id)) {
            throw conflict("ALREADY_OCCUPIES_DATE", "The employee already occupies this date.");
        }
        var eligibility = bookingEligibility(context, employee, period, now);
        if (!eligibility.allowed()) {
            throw conflict(eligibility.reason(), "Open booking is not available for this employee and period.");
        }
        var selectedSeat = validateSelectedSeat(context, period, physicalSeatId);
        var funding = funding(context.round(), employee, eligibility.price(), now);
        var source = eligibility.type() == PostAllocationBookingType.ORIGINAL_REBOOKING
                ? OccupancySourceType.ORIGINAL_REBOOKING : OccupancySourceType.OPEN_BOOKING;
        var occupancy = createOccupancy(context, employee, period, selectedSeat, source,
                eligibility.originalAssignment(), now);
        var booking = createBooking(occupancy, eligibility.type(), null, eligibility.price(), funding, now);
        applyFunding(context.round(), employee, booking, funding, now);
        reconcileAfterCapacityChange(context, now);
        return booking.id;
    }

    @Transactional
    public SeatAvailabilityResponse availability(long roundId, LocalDate targetDate, AttendancePeriod period) {
        var employee = identity.resolve();
        var context = lockContext(roundId, targetDate);
        var now = clock.instant();
        requireBeforeEnd(context.date(), now);
        expireOffers(context, now);
        if (context.round().capacitySource != CapacitySource.FLOOR_PLAN) {
            throw ApplicationProblem.notFound("FLOOR_PLAN_NOT_AVAILABLE", "This round does not use a floor plan.");
        }
        boolean pendingClaim = candidates.hasPending(employee.id, context.date().id, period);
        boolean openBooking = bookingEligibility(context, employee, period, now).allowed();
        if (!pendingClaim && !openBooking) {
            throw ApplicationProblem.notFound("SEAT_ACTION_NOT_AVAILABLE",
                    "No current claim or booking action permits this seat-availability view.");
        }
        var available = availableSeats(context, period).stream()
                .map(seat -> new SeatAvailabilityResponse.AvailableSeat(seat.id, seat.seatNumber)).toList();
        return new SeatAvailabilityResponse(roundId, targetDate, period, context.round().floorPlanVersion.id,
                now, available);
    }

    public BookingEligibility bookingEligibility(RoundDateEntity date, EmployeeEntity employee,
            AttendancePeriod period, Instant now) {
        var state = capacityStates.findForDate(date.id).orElse(null);
        if (state == null) {
            return BookingEligibility.denied("CAPACITY_STATE_UNAVAILABLE");
        }
        return bookingEligibility(new LockedContext(date.round, date, state), employee, period, now);
    }

    public int availabilityCount(RoundDateEntity date, AttendancePeriod period) {
        var state = capacityStates.findForDate(date.id).orElse(null);
        if (state == null) {
            return 0;
        }
        var context = new LockedContext(date.round, date, state);
        return date.round.capacitySource == CapacitySource.FLOOR_PLAN
                ? availableSeats(context, period).size() : availableSlots(context, period);
    }

    public RelinquishmentEligibility relinquishmentEligibility(SeatOccupancyEntity occupancy, Instant now) {
        if (!now.isBefore(targetDateEnd(occupancy.roundDate))) {
            return RelinquishmentEligibility.unavailable("TARGET_DATE_ENDED");
        }
        if (occupancy.sourceType == OccupancySourceType.ORIGINAL_ASSIGNMENT) {
            return RelinquishmentEligibility.available();
        }
        var booking = bookings.findForOccupancy(occupancy.id).orElse(null);
        if (booking == null) {
            throw new IllegalStateException("Later occupancy has no funding record");
        }
        if (booking.carryInTokensSpent == 0 || refundableParticipationOpen(booking, now)) {
            return RelinquishmentEligibility.available();
        }
        return RelinquishmentEligibility.unavailable("REFUND_WINDOW_ENDED");
    }

    @Transactional
    public void materializeExpiredCredits(long employeeId, long roundId, Instant now) {
        for (var credit : credits.findExpiredForUpdate(employeeId, roundId, now)) {
            int expired = credit.remainingTokens;
            credit.remainingTokens = 0;
            credit.expiredAt = now;
            addLedger(credit.employee, credit.round, LedgerType.CREDIT_EXPIRY, -expired,
                    "credit:" + credit.id + ":expiry", now, null, null, credit);
        }
    }

    private BookingEligibility bookingEligibility(LockedContext context, EmployeeEntity employee,
            AttendancePeriod period, Instant now) {
        if (occupancies.hasActive(context.date().id, employee.id)) {
            return BookingEligibility.denied("ALREADY_OCCUPIES_DATE");
        }
        if (!now.isBefore(targetDateEnd(context.date()))) {
            return BookingEligibility.denied("TARGET_DATE_ENDED");
        }
        if (!openCapacityAvailable(context, period)) {
            return BookingEligibility.denied("CAPACITY_UNAVAILABLE");
        }
        if (occupancies.hasRelinquishedLaterBooking(context.date().id, employee.id)) {
            return BookingEligibility.denied("LATER_BOOKING_RELINQUISHED");
        }
        var original = assignments.findForEmployeeDate(context.date().id, employee.id).orElse(null);
        if (original == null) {
            int price = openPrice(context.date(), period);
            return affordableBooking(context.round(), employee, price, PostAllocationBookingType.OPEN_BOOKING,
                    null, now);
        }
        if (!original.assigned) {
            return BookingEligibility.denied("ORIGINAL_UNSUCCESSFUL_BID");
        }
        var originalOccupancy = occupancies.findOriginal(original.id).orElse(null);
        if (originalOccupancy == null || originalOccupancy.status != OccupancyStatus.RELINQUISHED
                || original.attendancePeriod != period) {
            return BookingEligibility.denied("ORIGINAL_PERIOD_ONLY");
        }
        return affordableBooking(context.round(), employee, original.bid.tokens,
                PostAllocationBookingType.ORIGINAL_REBOOKING, original, now);
    }

    private BookingEligibility affordableBooking(BiddingRoundEntity round, EmployeeEntity employee, int price,
            PostAllocationBookingType type, SeatAssignmentEntity original, Instant now) {
        if (fundsAvailable(round, employee.id, now) < price) {
            return new BookingEligibility(false, price, type, original,
                    "INSUFFICIENT_POST_ALLOCATION_FUNDS");
        }
        return new BookingEligibility(true, price, type, original, null);
    }

    private LockedContext lockContext(long roundId, LocalDate targetDate) {
        var round = rounds.findById(roundId);
        if (round == null || round.status != de.gigaworks.seatbidding.persistence.RoundStatus.COMPLETED) {
            throw ApplicationProblem.notFound("PUBLISHED_ROUND_NOT_FOUND", "The completed round was not found.");
        }
        var date = dates.findForRoundAndDate(roundId, targetDate).orElseThrow(() ->
                ApplicationProblem.notFound("ROUND_DATE_NOT_FOUND", "The date does not belong to the completed round."));
        var state = capacityStates.findForDateForUpdate(date.id).orElseThrow(() ->
                ApplicationProblem.conflict("CAPACITY_STATE_UNAVAILABLE", "Capacity unavailable",
                        "Post-allocation capacity was not initialized for this date."));
        occupancies.findActiveForDateForUpdate(date.id);
        offers.findActiveForDateForUpdate(date.id);
        return new LockedContext(round, date, state);
    }

    private void createCredit(SeatOccupancyEntity occupancy, BiddingRoundEntity round, EmployeeEntity employee,
            Instant now) {
        if (credits.findForAssignment(occupancy.originalSeatAssignment.id).isPresent()) {
            return;
        }
        var credit = new RelinquishmentCreditEntity();
        credit.seatAssignment = occupancy.originalSeatAssignment;
        credit.employee = employee;
        credit.round = round;
        credit.initialTokens = occupancy.originalSeatAssignment.bid.tokens;
        credit.remainingTokens = credit.initialTokens;
        credit.expiresAt = weekEnd(round);
        credits.persist(credit);
        credits.flush();
        addLedger(employee, round, LedgerType.RELINQUISHMENT_CREDIT, credit.initialTokens,
                "assignment:" + occupancy.originalSeatAssignment.id + ":credit", now, null, null, credit);
    }

    private void activateNextTier(ReleasedCapacityOfferEntity offer, long releaserId, Instant now,
            LockedContext context) {
        if (!now.isBefore(offer.deadline) || !hasCapacity(context, offer.attendancePeriod)) {
            offer.status = now.isBefore(offer.deadline) ? OfferStatus.FILLED : OfferStatus.EXPIRED;
            return;
        }
        var existingBidIds = candidates.findForOffer(offer.id).stream().map(value -> value.bid.id)
                .collect(java.util.stream.Collectors.toSet());
        var eligible = assignments.findUnassignedForDate(context.date().id).stream()
                .filter(value -> !existingBidIds.contains(value.bid.id))
                .filter(value -> !value.bid.participation.employee.id.equals(releaserId))
                .filter(value -> !occupancies.hasActive(context.date().id, value.bid.participation.employee.id))
                .filter(value -> !occupancies.hasRelinquishedLaterBooking(context.date().id,
                        value.bid.participation.employee.id))
                .filter(value -> !candidates.hasAnyPending(value.bid.participation.employee.id, context.date().id))
                .filter(value -> hasCapacity(context, value.attendancePeriod)).toList();
        if (eligible.isEmpty()) {
            offer.status = OfferStatus.EXHAUSTED;
            return;
        }
        int tier = eligible.stream().mapToInt(value -> value.bid.tokens).max().orElseThrow();
        boolean pending = false;
        for (var assignment : eligible) {
            if (assignment.bid.tokens != tier) {
                continue;
            }
            var candidate = new SeatOfferCandidateEntity();
            candidate.offer = offer;
            candidate.roundDate = offer.roundDate;
            candidate.bid = assignment.bid;
            candidate.employee = assignment.bid.participation.employee;
            candidate.attendancePeriod = assignment.attendancePeriod;
            candidate.tierTokens = assignment.bid.tokens;
            candidate.requiredTokens = assignment.bid.tokens;
            candidate.offeredAt = now;
            if (fundsAvailable(context.round(), candidate.employee.id, now) >= candidate.requiredTokens) {
                candidate.status = OfferCandidateStatus.PENDING;
                pending = true;
            }
            else {
                candidate.status = OfferCandidateStatus.AUTO_DECLINED_INSUFFICIENT_FUNDS;
                candidate.respondedAt = now;
            }
            candidates.persist(candidate);
            candidates.flush();
            if (candidate.status == OfferCandidateStatus.PENDING) {
                createNotificationIntent(candidate);
            }
        }
        if (!pending) {
            activateNextTier(offer, releaserId, now, context);
        }
    }

    private void progressOffer(ReleasedCapacityOfferEntity offer, Instant now, LockedContext context) {
        boolean pending = candidates.findForOffer(offer.id).stream()
                .anyMatch(value -> value.status == OfferCandidateStatus.PENDING);
        if (!pending) {
            activateNextTier(offer, offer.relinquishment.employee.id, now, context);
        }
    }

    private void createNotificationIntent(SeatOfferCandidateEntity candidate) {
        boolean enabled = notificationSettings.findForEmployee(candidate.employee.id)
                .map(value -> value.seatOfferNotificationsEnabled).orElse(false);
        if (!enabled) {
            return;
        }
        var dispatch = new SeatOfferNotificationDispatchEntity();
        dispatch.candidate = candidate;
        dispatch.employee = candidate.employee;
        dispatch.status = "PENDING";
        offerDispatches.persist(dispatch);
        offerDispatches.flush();
        notificationEvents.fire(new SeatOfferNotificationRequested(candidate.id));
    }

    private void expireOffers(LockedContext context, Instant now) {
        for (var offer : offers.findActiveForDateForUpdate(context.date().id)) {
            if (now.isBefore(offer.deadline)) {
                continue;
            }
            for (var candidate : candidates.findForOffer(offer.id)) {
                if (candidate.status == OfferCandidateStatus.PENDING) {
                    candidate.status = OfferCandidateStatus.EXPIRED;
                    candidate.respondedAt = now;
                }
            }
            offer.status = OfferStatus.EXPIRED;
        }
    }

    private void reconcileAfterCapacityChange(LockedContext context, Instant now) {
        for (var offer : offers.findActiveForDateForUpdate(context.date().id)) {
            for (var candidate : candidates.findForOffer(offer.id)) {
                if (candidate.status == OfferCandidateStatus.PENDING && !hasCapacity(context, candidate.attendancePeriod)) {
                    candidate.status = OfferCandidateStatus.CLAIMED_BY_OTHER;
                    candidate.respondedAt = now;
                }
            }
            progressOffer(offer, now, context);
        }
    }

    private Funding funding(BiddingRoundEntity publishedRound, EmployeeEntity employee, int price, Instant now) {
        materializeExpiredCredits(employee.id, publishedRound.id, now);
        var usableCredits = credits.findSpendableForUpdate(employee.id, publishedRound.id, now);
        int creditAvailable = usableCredits.stream().mapToInt(value -> value.remainingTokens).sum();
        int creditSpend = Math.min(price, creditAvailable);
        int carryInSpend = price - creditSpend;
        if (carryInSpend == 0) {
            return new Funding(usableCredits, creditSpend, 0, null);
        }
        var openRound = rounds.findOpen().orElseThrow(() -> conflict("OPEN_ROUND_NOT_FOUND",
                "No open-round carry-over balance is available."));
        var participation = participations.findForUpdate(openRound.id, employee.id, configuration.lockTimeout())
                .orElseThrow(() -> conflict("PARTICIPATION_NOT_FOUND", "No open-round token balance was found."));
        int committed = bids.findForParticipation(participation.id).stream().mapToInt(value -> value.tokens).sum();
        int carryInAvailable = PostAllocationFunds.availableForLateBookings(participation.grantTokens,
                participation.uncappedCarriedInTokens, participation.postAllocationSpendTokens, committed);
        if (carryInSpend > carryInAvailable) {
            throw conflict("INSUFFICIENT_POST_ALLOCATION_FUNDS", "The booking price is no longer affordable.");
        }
        return new Funding(usableCredits, creditSpend, carryInSpend, participation);
    }

    private int fundsAvailable(BiddingRoundEntity publishedRound, long employeeId, Instant now) {
        return lateBookingFunds(publishedRound, employeeId, now).availableTokens();
    }

    public LateBookingFunds lateBookingFunds(BiddingRoundEntity publishedRound, long employeeId, Instant now) {
        int credit = credits.available(employeeId, publishedRound.id, now);
        var openRound = rounds.findOpen().orElse(null);
        if (openRound == null) {
            return new LateBookingFunds(credit, credit, 0);
        }
        var participation = participations.findForEmployee(openRound.id, employeeId).orElse(null);
        if (participation == null) {
            return new LateBookingFunds(credit, credit, 0);
        }
        int committed = bids.findForParticipation(participation.id).stream().mapToInt(value -> value.tokens).sum();
        int carryIn = PostAllocationFunds.availableForLateBookings(participation.grantTokens,
                participation.uncappedCarriedInTokens, participation.postAllocationSpendTokens, committed);
        return new LateBookingFunds(Math.addExact(credit, carryIn), credit, carryIn);
    }

    private PostAllocationBookingEntity createBooking(SeatOccupancyEntity occupancy, PostAllocationBookingType type,
            SeatOfferCandidateEntity candidate, int price, Funding funding, Instant now) {
        var booking = new PostAllocationBookingEntity();
        booking.occupancy = occupancy;
        booking.bookingType = type;
        booking.offerCandidate = candidate;
        booking.requiredTokens = price;
        booking.creditTokensSpent = funding.creditSpend();
        booking.carryInTokensSpent = funding.carryInSpend();
        booking.carryInParticipation = funding.carryInSpend() == 0 ? null : funding.participation();
        bookings.persist(booking);
        bookings.flush();
        return booking;
    }

    private void applyFunding(BiddingRoundEntity publishedRound, EmployeeEntity employee,
            PostAllocationBookingEntity booking, Funding funding, Instant now) {
        int remaining = funding.creditSpend();
        for (var credit : funding.credits()) {
            if (remaining == 0) {
                break;
            }
            int spend = Math.min(remaining, credit.remainingTokens);
            credit.remainingTokens -= spend;
            if (credit.remainingTokens == 0) {
                credit.fullyConsumedAt = now;
            }
            var link = new PostAllocationBookingCreditEntity();
            link.booking = booking;
            link.credit = credit;
            link.tokens = spend;
            bookingCredits.persist(link);
            addLedger(employee, publishedRound, LedgerType.CREDIT_SPEND, -spend,
                    "booking:" + booking.id + ":credit:" + credit.id, now, null, booking, credit);
            remaining -= spend;
        }
        if (funding.carryInSpend() > 0) {
            funding.participation().postAllocationSpendTokens += funding.carryInSpend();
            addLedger(employee, funding.participation().round, LedgerType.POST_ALLOCATION_SPEND,
                    -funding.carryInSpend(), "booking:" + booking.id + ":carry-in", now,
                    funding.participation(), booking, null);
        }
    }

    private void requireRefundable(PostAllocationBookingEntity booking, Instant now) {
        if (booking.refundedAt != null) {
            throw conflict("BOOKING_ALREADY_REFUNDED", "The later booking has already been refunded.");
        }
        if (booking.carryInTokensSpent > 0 && !refundableParticipationOpen(booking, now)) {
            throw conflict("REFUND_WINDOW_ENDED",
                    "The carry-over funding round has closed, so this booking can no longer be relinquished.");
        }
    }

    private static boolean refundableParticipationOpen(PostAllocationBookingEntity booking, Instant now) {
        return booking.carryInParticipation != null
                && booking.carryInParticipation.round.status == RoundStatus.OPEN
                && now.isBefore(booking.carryInParticipation.round.cutoffAt);
    }

    private void refundBooking(PostAllocationBookingEntity booking, EmployeeEntity employee, Instant now) {
        for (var link : bookingCredits.findForBooking(booking.id)) {
            var credit = credits.findByIdForUpdate(link.credit.id).orElseThrow(() ->
                    new IllegalStateException("Booking credit funding record is missing"));
            credit.remainingTokens += link.tokens;
            if (credit.remainingTokens > credit.initialTokens || credit.expiredAt != null) {
                throw new IllegalStateException("Booking credit refund would violate credit accounting");
            }
            credit.fullyConsumedAt = null;
            addLedger(employee, credit.round, LedgerType.CREDIT_REFUND, link.tokens,
                    "booking:" + booking.id + ":credit:" + credit.id + ":refund", now,
                    null, booking, credit);
        }
        if (booking.carryInTokensSpent > 0) {
            var participation = participations.findByIdForUpdate(booking.carryInParticipation.id,
                    configuration.lockTimeout()).orElseThrow(() ->
                    new IllegalStateException("Booking carry-over participation is missing"));
            if (participation.postAllocationSpendTokens < booking.carryInTokensSpent) {
                throw new IllegalStateException("Booking refund exceeds recorded post-allocation spending");
            }
            participation.postAllocationSpendTokens -= booking.carryInTokensSpent;
            addLedger(employee, participation.round, LedgerType.POST_ALLOCATION_REFUND,
                    booking.carryInTokensSpent, "booking:" + booking.id + ":carry-in:refund", now,
                    participation, booking, null);
        }
        booking.refundedAt = now;
    }

    private SeatOccupancyEntity createOccupancy(LockedContext context, EmployeeEntity employee,
            AttendancePeriod period, PhysicalSeatEntity selectedSeat, OccupancySourceType source,
            SeatAssignmentEntity originalAssignment, Instant now) {
        if (!hasCapacity(context, period)) {
            throw conflict("CAPACITY_TAKEN", "Compatible capacity was claimed by another employee.");
        }
        var occupancy = new SeatOccupancyEntity();
        occupancy.roundDate = context.date();
        occupancy.employee = employee;
        occupancy.attendancePeriod = period;
        occupancy.physicalSeat = selectedSeat;
        occupancy.sourceType = source;
        occupancy.originalSeatAssignment = originalAssignment;
        occupancy.status = OccupancyStatus.ACTIVE;
        occupancy.occupiedAt = now;
        occupancies.persist(occupancy);
        consume(context.state(), period);
        return occupancy;
    }

    private PhysicalSeatEntity validateSelectedSeat(LockedContext context, AttendancePeriod period, Long seatId) {
        if (context.round().capacitySource == CapacitySource.CONFIGURATION) {
            if (seatId != null) {
                throw ApplicationProblem.badRequest("PHYSICAL_SEAT_FORBIDDEN", "Physical seat is not accepted",
                        "Configuration-capacity rounds do not accept a physical seat identifier.");
            }
            return null;
        }
        if (seatId == null) {
            throw ApplicationProblem.badRequest("PHYSICAL_SEAT_REQUIRED", "Select a physical seat",
                    "A floor-plan claim or booking requires an explicit numbered seat selection.");
        }
        return availableSeats(context, period).stream().filter(value -> value.id.equals(seatId)).findFirst()
                .orElseThrow(() -> conflict("PHYSICAL_SEAT_UNAVAILABLE",
                        "The selected seat is no longer available; refresh and choose another seat."));
    }

    private List<PhysicalSeatEntity> availableSeats(LockedContext context, AttendancePeriod period) {
        if (context.round().floorPlanVersion == null) {
            return List.of();
        }
        Set<Long> guestIds = guestSeats.findForDate(context.date().id).stream()
                .map(value -> value.physicalSeat.id).collect(java.util.stream.Collectors.toSet());
        var active = occupancies.findActiveForDate(context.date().id);
        var result = new ArrayList<PhysicalSeatEntity>();
        for (var marker : versionSeats.findActiveForVersion(context.round().floorPlanVersion.id)) {
            var seat = marker.physicalSeat;
            if (guestIds.contains(seat.id)) {
                continue;
            }
            boolean morningBusy = active.stream().filter(value -> value.physicalSeat != null)
                    .filter(value -> value.physicalSeat.id.equals(seat.id))
                    .anyMatch(value -> value.attendancePeriod != AttendancePeriod.AFTERNOON_ONLY);
            boolean afternoonBusy = active.stream().filter(value -> value.physicalSeat != null)
                    .filter(value -> value.physicalSeat.id.equals(seat.id))
                    .anyMatch(value -> value.attendancePeriod != AttendancePeriod.MORNING_ONLY);
            if ((period == AttendancePeriod.MORNING_ONLY && !morningBusy)
                    || (period == AttendancePeriod.AFTERNOON_ONLY && !afternoonBusy)
                    || (period == AttendancePeriod.FULL_DAY && !morningBusy && !afternoonBusy)) {
                result.add(seat);
            }
        }
        result.sort(Comparator.comparingInt(value -> value.seatNumber));
        return result;
    }

    private boolean openCapacityAvailable(LockedContext context, AttendancePeriod period) {
        int free = context.round().capacitySource == CapacitySource.FLOOR_PLAN
                ? availableSeats(context, period).size() : availableSlots(context, period);
        long pending = candidates.countPendingCompatible(context.date().id, period);
        return free > pending;
    }

    private boolean hasCapacity(LockedContext context, AttendancePeriod period) {
        if (!vector(context).supports(period)) {
            return false;
        }
        return context.round().capacitySource != CapacitySource.FLOOR_PLAN
                || !availableSeats(context, period).isEmpty();
    }

    private int availableSlots(LockedContext context, AttendancePeriod period) {
        var vector = vector(context);
        return switch (period) {
            case FULL_DAY -> Math.min(vector.morningAvailable(), vector.afternoonAvailable());
            case MORNING_ONLY -> vector.morningAvailable();
            case AFTERNOON_ONLY -> vector.afternoonAvailable();
        };
    }

    private CapacityVector vector(LockedContext context) {
        int reserved = reservations.findByTargetDate(context.date().targetDate)
                .map(value -> value.reservedSeatCount).orElse(0);
        int assignable = context.round().seatCapacity - reserved;
        return new CapacityVector(assignable - context.state().morningOccupied,
                assignable - context.state().afternoonOccupied);
    }

    private int openPrice(RoundDateEntity date, AttendancePeriod period) {
        return switch (period) {
            case FULL_DAY -> assignments.findForDate(date.id).stream().filter(value -> value.assigned)
                    .mapToInt(value -> value.allocationUnit.scoreTokens).min()
                    .orElse(date.round.minimumOpenBookingPrice);
            case MORNING_ONLY -> assignments.findForDate(date.id).stream().filter(value -> value.assigned)
                    .filter(value -> value.attendancePeriod != AttendancePeriod.AFTERNOON_ONLY)
                    .mapToInt(value -> value.bid.tokens).min().orElse(date.round.minimumOpenBookingPrice);
            case AFTERNOON_ONLY -> assignments.findForDate(date.id).stream().filter(value -> value.assigned)
                    .filter(value -> value.attendancePeriod != AttendancePeriod.MORNING_ONLY)
                    .mapToInt(value -> value.bid.tokens).min().orElse(date.round.minimumOpenBookingPrice);
        };
    }

    private static void consume(RoundDateCapacityStateEntity state, AttendancePeriod period) {
        if (period != AttendancePeriod.AFTERNOON_ONLY) state.morningOccupied++;
        if (period != AttendancePeriod.MORNING_ONLY) state.afternoonOccupied++;
    }

    private static void release(RoundDateCapacityStateEntity state, AttendancePeriod period) {
        if (period != AttendancePeriod.AFTERNOON_ONLY) state.morningOccupied--;
        if (period != AttendancePeriod.MORNING_ONLY) state.afternoonOccupied--;
        if (state.morningOccupied < 0 || state.afternoonOccupied < 0) {
            throw new IllegalStateException("Occupancy capacity counters became negative");
        }
    }

    private Instant targetDateEnd(RoundDateEntity date) {
        return date.targetDate.plusDays(1).atStartOfDay(ZoneId.of(date.round.scheduleZone)).toInstant();
    }

    private Instant weekEnd(BiddingRoundEntity round) {
        var last = dates.findForRound(round.id).stream().max(Comparator.comparing(value -> value.targetDate))
                .orElseThrow();
        return targetDateEnd(last);
    }

    private void requireBeforeEnd(RoundDateEntity date, Instant now) {
        if (!now.isBefore(targetDateEnd(date))) {
            throw conflict("TARGET_DATE_ENDED", "The target date has ended.");
        }
    }

    private void addLedger(EmployeeEntity employee, BiddingRoundEntity round, LedgerType type, int amount,
            String key, Instant now, RoundParticipationEntity participation, PostAllocationBookingEntity booking,
            RelinquishmentCreditEntity credit) {
        var entry = new TokenLedgerEntity();
        entry.employee = employee;
        entry.round = round;
        entry.participation = participation;
        entry.postAllocationBooking = booking;
        entry.relinquishmentCredit = credit;
        entry.type = type;
        entry.amount = amount;
        entry.idempotencyKey = key;
        entry.occurredAt = now;
        ledger.persist(entry);
    }

    private static Instant min(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private static ApplicationProblem conflict(String code, String detail) {
        return ApplicationProblem.conflict(code, "Post-allocation action unavailable", detail);
    }

    private record LockedContext(BiddingRoundEntity round, RoundDateEntity date,
            RoundDateCapacityStateEntity state) {
    }

    private record Funding(List<RelinquishmentCreditEntity> credits, int creditSpend, int carryInSpend,
            RoundParticipationEntity participation) {
    }

    public record RelinquishmentEligibility(boolean allowed, String reason) {

        static RelinquishmentEligibility available() {
            return new RelinquishmentEligibility(true, null);
        }

        static RelinquishmentEligibility unavailable(String reason) {
            return new RelinquishmentEligibility(false, reason);
        }
    }

    public record LateBookingFunds(int availableTokens, int relinquishmentCreditTokens, int carryInTokens) {
    }

    public record BookingEligibility(boolean allowed, int price, PostAllocationBookingType type,
            SeatAssignmentEntity originalAssignment, String reason) {

        static BookingEligibility denied(String reason) {
            return new BookingEligibility(false, 0, null, null, reason);
        }
    }

    public enum ClaimResult {
        CLAIMED,
        CAPACITY_TAKEN,
        INSUFFICIENT_FUNDS,
        EXPIRED
    }
}