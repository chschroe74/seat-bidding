package de.gigaworks.seatbidding.floorplan;

import de.gigaworks.seatbidding.auth.EmployeeIdentityService;
import de.gigaworks.seatbidding.dto.AdminFloorPlanResponse;
import de.gigaworks.seatbidding.dto.ConfirmedRevisionRequest;
import de.gigaworks.seatbidding.dto.FloorPlanResponse;
import de.gigaworks.seatbidding.dto.FloorPlanSeatMutationRequest;
import de.gigaworks.seatbidding.dto.GuestSeatPreferencesResponse;
import de.gigaworks.seatbidding.dto.ReplaceGuestSeatPreferencesRequest;
import de.gigaworks.seatbidding.dto.ReplaceSeatPreferencesRequest;
import de.gigaworks.seatbidding.dto.RevisionRequest;
import de.gigaworks.seatbidding.exception.ApplicationProblem;
import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.persistence.CapacitySource;
import de.gigaworks.seatbidding.persistence.EmployeeSeatPreferenceEntity;
import de.gigaworks.seatbidding.persistence.EmployeeRepository;
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
import de.gigaworks.seatbidding.persistence.RoundDateRepository;
import de.gigaworks.seatbidding.persistence.SeatReservationRepository;
import de.gigaworks.seatbidding.round.SeatBiddingConfiguration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FloorPlanService {

    @Inject EmployeeIdentityService identity;
    @Inject FloorPlanRepository plans;
    @Inject FloorPlanVersionRepository versions;
    @Inject FloorPlanVersionSeatRepository markers;
    @Inject PhysicalSeatRepository physicalSeats;
    @Inject EmployeeSeatPreferenceRevisionRepository preferenceRevisions;
    @Inject EmployeeRepository employees;
    @Inject EmployeeSeatPreferenceRepository preferences;
    @Inject GuestSeatPreferenceRevisionRepository guestPreferenceRevisions;
    @Inject GuestSeatPreferenceRepository guestPreferences;
    @Inject BiddingRoundRepository rounds;
    @Inject RoundDateRepository roundDates;
    @Inject SeatBiddingConfiguration configuration;
    @Inject SeatReservationRepository reservations;
    @Inject SvgSanitizer sanitizer;
    @Inject FloorPlanPublicationPolicy publicationPolicy;
    @Inject Clock clock;

    @Transactional
    public AdminFloorPlanResponse adminState() {
        identity.requireCurrentAdmin();
        return plans.findSingleton().map(this::adminResponse)
                .orElse(new AdminFloorPlanResponse(0, 0, null, null, false, null, List.of(), null, null));
    }

    @Transactional
    public GuestSeatPreferencesResponse guestPreferences() {
        identity.requireCurrentAdmin();
        var round = rounds.findOpen().filter(value -> value.floorPlanVersion != null).orElseThrow(() ->
                ApplicationProblem.notFound("FLOOR_PLAN_NOT_FOUND", "The current round has no floor plan."));
        return guestResponse(round);
    }

    @Transactional
    public GuestSeatPreferencesResponse replaceGuestPreferences(ReplaceGuestSeatPreferencesRequest request) {
        var admin = identity.requireCurrentAdmin();
        var visible = rounds.findOpen().filter(value -> value.floorPlanVersion != null).orElseThrow(() ->
                ApplicationProblem.notFound("FLOOR_PLAN_NOT_FOUND", "The current round has no floor plan."));
        var round = rounds.findByIdForUpdate(visible.id).filter(value -> value.floorPlanVersion != null)
                .orElseThrow(() -> ApplicationProblem.conflict("ROUND_PROCESSING", "The round changed",
                        "Refresh before saving."));
        if (round.status != de.gigaworks.seatbidding.persistence.RoundStatus.OPEN
                || !round.floorPlanVersion.id.equals(request.floorPlanVersionId())) {
            throw ApplicationProblem.conflict("STALE_FLOOR_PLAN", "The floor plan changed", "Refresh before saving.");
        }
        var plan = plans.findSingletonForUpdate().filter(value ->
                value.id.equals(round.floorPlanVersion.floorPlan.id)).orElseThrow(() ->
                        ApplicationProblem.conflict("STALE_FLOOR_PLAN", "The floor plan changed",
                                "Refresh before saving."));
        var latest = guestPreferenceRevisions.findLatest(plan.id).orElse(null);
        long latestRevision = latest == null ? 0 : latest.revisionNo;
        if (latestRevision != request.preferenceRevision()) {
            throw ApplicationProblem.conflict("STALE_GUEST_PREFERENCES", "Guest preferences changed",
                    "Refresh before saving.");
        }
        var active = markers.findActiveForVersion(round.floorPlanVersion.id);
        var activeIds = active.stream().map(value -> value.physicalSeat.id).collect(java.util.stream.Collectors.toSet());
        validatePreferences(request.preferences().stream()
                .map(value -> new SubmittedPreference(value.seatId(), value.stars())).toList(), activeIds,
                "INVALID_GUEST_SEAT_PREFERENCES");

        var revision = new GuestSeatPreferenceRevisionEntity();
        revision.floorPlan = plan;
        revision.revisionNo = latestRevision + 1;
        revision.createdBy = admin;
        revision.createdAt = clock.instant();
        guestPreferenceRevisions.persist(revision);
        var seatsById = active.stream().collect(java.util.stream.Collectors.toMap(value -> value.physicalSeat.id,
                value -> value.physicalSeat));
        for (var submitted : request.preferences()) {
            var preference = new GuestSeatPreferenceEntity();
            preference.revision = revision;
            preference.physicalSeat = seatsById.get(submitted.seatId());
            preference.stars = (short) submitted.stars();
            guestPreferences.persist(preference);
        }
        guestPreferenceRevisions.flush();
        return guestResponse(round);
    }

    @Transactional
    public UploadResult upload(byte[] bytes) {
        var admin = identity.requireCurrentAdmin();
        var sanitized = sanitizer.sanitize(bytes);
        var plan = plans.findSingletonForUpdate().orElseGet(() -> {
            var created = new FloorPlanEntity();
            plans.persist(created);
            plans.flush();
            return created;
        });
        if (plan.pendingVersion != null || plan.pendingDeactivation) {
            throw ApplicationProblem.conflict("FLOOR_PLAN_CHANGE_PENDING", "A floor-plan change is pending",
                    "Wait for the next round before creating another draft.");
        }
        var draft = versions.findDraft(plan.id).orElse(null);
        boolean created = draft == null;
        var warnings = draft == null ? List.<String>of() : uploadWarnings(draft, sanitized);
        if (draft == null) {
            draft = new FloorPlanVersionEntity();
            draft.floorPlan = plan;
            draft.versionNo = versions.nextVersion(plan.id);
            draft.baseVersion = plan.activeVersion == null
                    ? versions.findLatestPublished(plan.id).orElse(null) : plan.activeVersion;
            draft.status = FloorPlanVersionStatus.DRAFT;
            draft.createdBy = admin;
        }
        draft.sanitizedSvg = sanitized.content();
        draft.contentSha256 = sanitized.sha256();
        draft.viewBoxMinX = sanitized.minX();
        draft.viewBoxMinY = sanitized.minY();
        draft.viewBoxWidth = sanitized.width();
        draft.viewBoxHeight = sanitized.height();
        if (created) {
            versions.persist(draft);
            versions.flush();
            if (draft.baseVersion != null) {
                for (var existing : markers.findActiveForVersion(draft.baseVersion.id)) {
                    var copy = new FloorPlanVersionSeatEntity();
                    copy.version = draft;
                    copy.physicalSeat = existing.physicalSeat;
                    copy.seatNumber = existing.seatNumber;
                    copy.normalizedX = existing.normalizedX;
                    copy.normalizedY = existing.normalizedY;
                    copy.state = FloorPlanSeatState.ACTIVE;
                    markers.persist(copy);
                }
            }
        }
        draft.draftRevision++;
        plan.stateRevision++;
        return new UploadResult(created, adminResponse(plan, warnings));
    }

    @Transactional
    public AdminFloorPlanResponse addSeat(FloorPlanSeatMutationRequest request) {
        identity.requireCurrentAdmin();
        var plan = requirePlanForUpdate();
        var draft = requireDraft(plan);
        checkRevision(draft, request.draftRevision());
        long active = markers.findActiveForVersion(draft.id).size();
        if (active >= configuration.floorPlan().maxActiveSeats()) {
            throw ApplicationProblem.conflict("FLOOR_PLAN_SEAT_LIMIT", "Seat limit reached",
                    "The configured floor-plan seat limit has been reached.");
        }
        var seat = new PhysicalSeatEntity();
        seat.floorPlan = plan;
        seat.seatNumber = nextDraftNumber(plan, draft);
        physicalSeats.persist(seat);
        var marker = new FloorPlanVersionSeatEntity();
        marker.version = draft;
        marker.physicalSeat = seat;
        marker.seatNumber = seat.seatNumber;
        marker.normalizedX = request.normalizedX();
        marker.normalizedY = request.normalizedY();
        marker.state = FloorPlanSeatState.ACTIVE;
        markers.persist(marker);
        draft.draftRevision++;
        return adminResponse(plan);
    }

    @Transactional
    public AdminFloorPlanResponse moveSeat(long seatId, FloorPlanSeatMutationRequest request) {
        identity.requireCurrentAdmin();
        var plan = requirePlanForUpdate();
        var draft = requireDraft(plan);
        checkRevision(draft, request.draftRevision());
        var marker = requireMarker(draft, seatId);
        if (marker.state != FloorPlanSeatState.ACTIVE) {
            throw ApplicationProblem.conflict("SEAT_PENDING_REMOVAL", "Seat cannot be moved",
                    "Restore the seat before moving it.");
        }
        marker.normalizedX = request.normalizedX();
        marker.normalizedY = request.normalizedY();
        draft.draftRevision++;
        return adminResponse(plan);
    }

    @Transactional
    public AdminFloorPlanResponse deleteSeat(long seatId, RevisionRequest request) {
        identity.requireCurrentAdmin();
        var plan = requirePlanForUpdate();
        var draft = requireDraft(plan);
        checkRevision(draft, request.revision());
        var marker = requireMarker(draft, seatId);
        if (marker.physicalSeat.firstPublishedVersion != null) {
            marker.state = FloorPlanSeatState.PENDING_REMOVAL;
        }
        else {
            int removedNumber = marker.seatNumber;
            markers.delete(marker);
            markers.flush();
            physicalSeats.delete(marker.physicalSeat);
            physicalSeats.flush();
            for (var later : markers.findForVersion(draft.id)) {
                if (later.physicalSeat.firstPublishedVersion == null && later.seatNumber > removedNumber) {
                    later.seatNumber--;
                    later.physicalSeat.seatNumber--;
                }
            }
        }
        draft.draftRevision++;
        return adminResponse(plan);
    }

    @Transactional
    public AdminFloorPlanResponse restoreSeat(long seatId, RevisionRequest request) {
        identity.requireCurrentAdmin();
        var plan = requirePlanForUpdate();
        var draft = requireDraft(plan);
        checkRevision(draft, request.revision());
        var marker = requireMarker(draft, seatId);
        marker.state = FloorPlanSeatState.ACTIVE;
        draft.draftRevision++;
        return adminResponse(plan);
    }

    @Transactional
    public AdminFloorPlanResponse removeAll(ConfirmedRevisionRequest request) {
        identity.requireCurrentAdmin();
        if (!request.confirmed()) {
            throw ApplicationProblem.badRequest("CONFIRMATION_REQUIRED", "Confirmation required",
                    "Removing all seats must be explicitly confirmed.");
        }
        var plan = requirePlanForUpdate();
        var draft = requireDraft(plan);
        checkRevision(draft, request.revision());
        for (var marker : List.copyOf(markers.findForVersion(draft.id))) {
            if (marker.physicalSeat.firstPublishedVersion == null) {
                markers.delete(marker);
                markers.flush();
                physicalSeats.delete(marker.physicalSeat);
                physicalSeats.flush();
            }
            else {
                marker.state = FloorPlanSeatState.PENDING_REMOVAL;
            }
        }
        draft.draftRevision++;
        return adminResponse(plan);
    }

    @Transactional
    public AdminFloorPlanResponse publish(RevisionRequest request) {
        identity.requireCurrentAdmin();
        var round = lockVisibleOpenRound();
        var plan = requirePlanForUpdate();
        if (plan.pendingVersion != null || plan.pendingDeactivation) {
            throw ApplicationProblem.conflict("FLOOR_PLAN_CHANGE_PENDING", "A floor-plan change is pending",
                    "Wait for the next round before publishing another change.");
        }
        var draft = requireDraft(plan);
        checkRevision(draft, request.revision());
        var active = markers.findActiveForVersion(draft.id);
        if (active.isEmpty()) {
            throw ApplicationProblem.badRequest("EMPTY_FLOOR_PLAN", "Floor plan has no seats",
                    "Add at least one active seat before publishing.");
        }
        validateFutureReservations(active.size());
        var publishedAt = clock.instant();
        var decision = publicationPolicy.decide(round, active.size(), publishedAt);
        plan.permanentNumberWatermark = Math.max(plan.permanentNumberWatermark,
                active.stream().mapToInt(value -> value.seatNumber).max().orElse(0));
        draft.status = FloorPlanVersionStatus.PUBLISHED;
        draft.publishedAt = publishedAt;
        for (var marker : markers.findForVersion(draft.id)) {
            if (marker.state == FloorPlanSeatState.ACTIVE && marker.physicalSeat.firstPublishedVersion == null) {
                marker.physicalSeat.firstPublishedVersion = draft;
            }
            if (marker.state == FloorPlanSeatState.PENDING_REMOVAL) {
                marker.physicalSeat.retiredInVersion = draft;
                markers.delete(marker);
            }
        }
        if (decision.immediate()) {
            plan.activeVersion = draft;
            round.capacitySource = CapacitySource.FLOOR_PLAN;
            round.floorPlanVersion = draft;
        }
        else {
            plan.pendingVersion = draft;
        }
        plan.stateRevision++;
        return adminResponse(plan, List.of(), publicationTiming(decision, round, active.size()));
    }

    @Transactional
    public void discardDraft() {
        identity.requireCurrentAdmin();
        var plan = requirePlanForUpdate();
        var draft = requireDraft(plan);
        var draftMarkers = List.copyOf(markers.findForVersion(draft.id));
        var draftNewSeats = draftMarkers.stream().map(value -> value.physicalSeat)
                .filter(value -> value.firstPublishedVersion == null).toList();
        for (var marker : draftMarkers) {
            markers.delete(marker);
        }
        markers.flush();
        for (var seat : draftNewSeats) {
            physicalSeats.delete(seat);
        }
        physicalSeats.flush();
        versions.delete(draft);
        plan.stateRevision++;
    }

    @Transactional
    public AdminFloorPlanResponse deactivate(ConfirmedRevisionRequest request) {
        identity.requireCurrentAdmin();
        if (!request.confirmed()) {
            throw ApplicationProblem.badRequest("CONFIRMATION_REQUIRED", "Confirmation required",
                    "Floor-plan deactivation must be explicitly confirmed.");
        }
        lockVisibleOpenRound();
        var plan = requirePlanForUpdate();
        if (plan.pendingDeactivation) {
            return adminResponse(plan);
        }
        if (plan.activeVersion == null) {
            throw ApplicationProblem.notFound("FLOOR_PLAN_NOT_FOUND", "No active floor plan can be deactivated.");
        }
        if (request.revision() != plan.stateRevision) {
            stale();
        }
        if (versions.findDraft(plan.id).isPresent() || plan.pendingVersion != null) {
            throw ApplicationProblem.conflict("FLOOR_PLAN_CHANGE_PENDING", "A floor-plan change is pending",
                    "Discard the draft or wait for pending publication before deactivation.");
        }
        validateFutureReservations(configuration.seatCapacity());
        plan.pendingDeactivation = true;
        plan.stateRevision++;
        return adminResponse(plan);
    }

    @Transactional
    public FloorPlanResponse current() {
        var employee = identity.resolve();
        var round = rounds.findOpen().filter(value -> value.floorPlanVersion != null).orElseThrow(() ->
                ApplicationProblem.notFound("FLOOR_PLAN_NOT_FOUND", "The current round has no floor plan."));
        return employeeResponse(round, employee.id, true);
    }

    @Transactional
    public FloorPlanResponse latestAssignments() {
        var employee = identity.resolve();
        var round = rounds.findLatestCompleted().filter(value -> value.floorPlanVersion != null).orElseThrow(() ->
                ApplicationProblem.notFound("FLOOR_PLAN_NOT_FOUND", "The latest assignments have no floor plan."));
        return employeeResponse(round, employee.id, false);
    }

    @Transactional
    public FloorPlanResponse replacePreferences(ReplaceSeatPreferencesRequest request) {
        var employee = identity.resolve();
        var visibleRound = rounds.findOpen().filter(value -> value.floorPlanVersion != null).orElseThrow(() ->
                ApplicationProblem.notFound("FLOOR_PLAN_NOT_FOUND", "The current round has no floor plan."));
        var round = rounds.findByIdForUpdate(visibleRound.id).filter(value -> value.floorPlanVersion != null)
                .orElseThrow(() -> ApplicationProblem.conflict("ROUND_PROCESSING", "The round changed",
                        "Refresh before saving."));
        if (round.status != de.gigaworks.seatbidding.persistence.RoundStatus.OPEN) {
            throw ApplicationProblem.conflict("ROUND_PROCESSING", "The round changed", "Refresh before saving.");
        }
        if (!round.id.equals(request.roundId()) || !round.floorPlanVersion.id.equals(request.floorPlanVersionId())) {
            throw ApplicationProblem.conflict("STALE_FLOOR_PLAN", "The floor plan changed", "Refresh before saving.");
        }
        employee = employees.findByIdForUpdate(employee.id).orElseThrow(() ->
                ApplicationProblem.conflict("EMPLOYEE_CHANGED", "Account changed", "Refresh before saving."));
        var latest = preferenceRevisions.findLatestForUpdate(employee.id, round.floorPlanVersion.floorPlan.id)
                .orElse(null);
        long latestRevision = latest == null ? 0 : latest.revisionNo;
        if (latestRevision != request.preferenceRevision()) {
            throw ApplicationProblem.conflict("STALE_PREFERENCES", "Preferences changed", "Refresh before saving.");
        }
        var active = markers.findActiveForVersion(round.floorPlanVersion.id);
        var activeIds = active.stream().map(value -> value.physicalSeat.id).collect(java.util.stream.Collectors.toSet());
        var submitted = new HashSet<Long>();
        int threeStars = 0;
        for (var preference : request.preferences()) {
            if (!activeIds.contains(preference.seatId()) || !submitted.add(preference.seatId())
                    || preference.stars() < 1 || preference.stars() > 3) {
                throw ApplicationProblem.badRequest("INVALID_SEAT_PREFERENCES", "Invalid seat preferences",
                        "Preferences must contain distinct active seats with one to three stars.");
            }
            if (preference.stars() == 3) {
                threeStars++;
            }
        }
        if (threeStars > 1) {
            throw ApplicationProblem.badRequest("MULTIPLE_PREFERRED_SEATS", "Invalid seat preferences",
                    "Only one seat may have three stars.");
        }
        var revision = new EmployeeSeatPreferenceRevisionEntity();
        revision.employee = employee;
        revision.floorPlan = round.floorPlanVersion.floorPlan;
        revision.revisionNo = latestRevision + 1;
        revision.createdAt = clock.instant();
        preferenceRevisions.persist(revision);
        var seatsById = active.stream().collect(java.util.stream.Collectors.toMap(value -> value.physicalSeat.id,
                value -> value.physicalSeat));
        for (var submittedPreference : request.preferences()) {
            var preference = new EmployeeSeatPreferenceEntity();
            preference.revision = revision;
            preference.physicalSeat = seatsById.get(submittedPreference.seatId());
            preference.stars = (short) submittedPreference.stars();
            preferences.persist(preference);
        }
        preferenceRevisions.flush();
        return employeeResponse(round, employee.id, clock.instant().isBefore(round.cutoffAt));
    }

    @Transactional
    public FloorPlanVersionEntity authorizedVersion(long versionId) {
        var employee = identity.resolve();
        var plan = plans.findSingleton().orElse(null);
        var version = versions.findById(versionId);
        if (plan != null && version != null && version.status == FloorPlanVersionStatus.PUBLISHED
                && plan.activeVersion != null && plan.activeVersion.id.equals(versionId)) {
            return version;
        }
        if (employee.admin) {
            if (plan != null && version != null && (version.status == FloorPlanVersionStatus.DRAFT
                    || plan.pendingVersion != null && plan.pendingVersion.id.equals(versionId))) {
                return version;
            }
        }
        var open = rounds.findOpen().map(value -> value.floorPlanVersion).filter(value -> value != null
                && value.id.equals(versionId)).orElse(null);
        if (open != null) {
            return open;
        }
        return rounds.findLatestCompleted().map(value -> value.floorPlanVersion).filter(value -> value != null
                && value.id.equals(versionId)).orElseThrow(() ->
                ApplicationProblem.notFound("FLOOR_PLAN_NOT_FOUND", "That floor plan is not available."));
    }

    private FloorPlanResponse employeeResponse(de.gigaworks.seatbidding.persistence.BiddingRoundEntity round,
            long employeeId, boolean includePreferences) {
        var version = round.floorPlanVersion;
        var latest = includePreferences
                ? preferenceRevisions.findLatest(employeeId, version.floorPlan.id).orElse(null) : null;
        Map<Long, Integer> ratingBySeat = new HashMap<>();
        if (latest != null) {
            preferences.findForRevision(latest.id).forEach(value -> ratingBySeat.put(value.physicalSeat.id,
                    (int) value.stars));
        }
        var seats = markers.findActiveForVersion(version.id).stream().map(value -> new FloorPlanResponse.Seat(
                value.physicalSeat.id, value.seatNumber, value.normalizedX, value.normalizedY,
                ratingBySeat.getOrDefault(value.physicalSeat.id, 0))).toList();
        return new FloorPlanResponse(round.id, version.id, "/api/floor-plan/versions/" + version.id + "/svg",
                viewBox(version), round.cutoffAt, clock.instant(), latest == null ? 0 : latest.revisionNo,
                clock.instant().isBefore(round.cutoffAt), seats);
    }

    private GuestSeatPreferencesResponse guestResponse(
            de.gigaworks.seatbidding.persistence.BiddingRoundEntity round) {
        var version = round.floorPlanVersion;
        var latest = guestPreferenceRevisions.findLatest(version.floorPlan.id).orElse(null);
        Map<Long, Integer> ratings = new HashMap<>();
        if (latest != null) {
            guestPreferences.findForRevision(latest.id).forEach(value -> ratings.put(value.physicalSeat.id,
                    (int) value.stars));
        }
        var seats = markers.findActiveForVersion(version.id).stream().map(value ->
                new GuestSeatPreferencesResponse.Seat(value.physicalSeat.id, value.seatNumber,
                        ratings.getOrDefault(value.physicalSeat.id, 0))).toList();
        var now = clock.instant();
        return new GuestSeatPreferencesResponse(version.id, latest == null ? 0 : latest.revisionNo,
                round.cutoffAt, now, now.isBefore(round.cutoffAt), seats);
    }

    private static void validatePreferences(List<SubmittedPreference> preferences, java.util.Set<Long> activeIds,
            String code) {
        var submitted = new HashSet<Long>();
        int threeStars = 0;
        for (var preference : preferences) {
            if (!activeIds.contains(preference.seatId()) || !submitted.add(preference.seatId())
                    || preference.stars() < 1 || preference.stars() > 3) {
                throw ApplicationProblem.badRequest(code, "Invalid seat preferences",
                        "Preferences must contain distinct active seats with one to three stars.");
            }
            if (preference.stars() == 3) {
                threeStars++;
            }
        }
        if (threeStars > 1) {
            throw ApplicationProblem.badRequest("MULTIPLE_PREFERRED_SEATS", "Invalid seat preferences",
                    "Only one seat may have three stars.");
        }
    }

    private AdminFloorPlanResponse adminResponse(FloorPlanEntity plan) {
        return adminResponse(plan, List.of(), null);
    }

    private AdminFloorPlanResponse adminResponse(FloorPlanEntity plan, List<String> warnings) {
        return adminResponse(plan, warnings, null);
    }

    private AdminFloorPlanResponse adminResponse(FloorPlanEntity plan, List<String> warnings,
            AdminFloorPlanResponse.PublicationTiming publicationEffect) {
        return new AdminFloorPlanResponse(plan.stateRevision, plan.permanentNumberWatermark,
                adminVersion(plan.activeVersion), adminVersion(plan.pendingVersion), plan.pendingDeactivation,
                versions.findDraft(plan.id).map(this::adminVersion).orElse(null), warnings,
                publicationPreview(plan), publicationEffect);
    }

    private AdminFloorPlanResponse.PublicationTiming publicationPreview(FloorPlanEntity plan) {
        var draft = versions.findDraft(plan.id).orElse(null);
        if (draft == null) {
            return null;
        }
        int capacity = markers.findActiveForVersion(draft.id).size();
        if (plan.pendingVersion != null || plan.pendingDeactivation) {
            return new AdminFloorPlanResponse.PublicationTiming("NEXT_SUCCESSOR_ROUND", null, List.of(),
                    rounds.findOpen().map(value -> value.seatCapacity).orElse(null), capacity, "PENDING_CHANGE");
        }
        var round = rounds.findOpen().orElse(null);
        return publicationTiming(publicationPolicy.decide(round, capacity, clock.instant()), round, capacity);
    }

    private AdminFloorPlanResponse.PublicationTiming publicationTiming(FloorPlanPublicationPolicy.Decision decision,
            de.gigaworks.seatbidding.persistence.BiddingRoundEntity round, int draftCapacity) {
        boolean immediate = decision.immediate() && round != null;
        return new AdminFloorPlanResponse.PublicationTiming(decision.mode(), immediate ? round.id : null,
                immediate ? roundDates.findForRound(round.id).stream().map(value -> value.targetDate).toList()
                        : List.of(),
                round == null ? null : round.seatCapacity, draftCapacity, decision.reason());
    }

    private de.gigaworks.seatbidding.persistence.BiddingRoundEntity lockVisibleOpenRound() {
        return rounds.findOpen().flatMap(value -> rounds.findByIdForUpdate(value.id)).orElse(null);
    }

    private static List<String> uploadWarnings(FloorPlanVersionEntity draft, SvgSanitizer.SanitizedSvg replacement) {
        var warnings = new java.util.ArrayList<String>();
        boolean viewBoxChanged = draft.viewBoxMinX.compareTo(replacement.minX()) != 0
                || draft.viewBoxMinY.compareTo(replacement.minY()) != 0
                || draft.viewBoxWidth.compareTo(replacement.width()) != 0
                || draft.viewBoxHeight.compareTo(replacement.height()) != 0;
        if (viewBoxChanged) {
            warnings.add("The SVG view box changed. Review every preserved seat marker before publishing.");
        }
        if (draft.viewBoxWidth.multiply(replacement.height())
                .compareTo(draft.viewBoxHeight.multiply(replacement.width())) != 0) {
            warnings.add("The SVG aspect ratio changed. Preserved seat markers may no longer align with desks.");
        }
        return List.copyOf(warnings);
    }

    private AdminFloorPlanResponse.Version adminVersion(FloorPlanVersionEntity version) {
        if (version == null) {
            return null;
        }
        var seatResponses = markers.findForVersion(version.id).stream().map(value -> new AdminFloorPlanResponse.Seat(
                value.physicalSeat.id, value.seatNumber, value.normalizedX, value.normalizedY,
                value.physicalSeat.firstPublishedVersion != null, value.state.name())).toList();
        return new AdminFloorPlanResponse.Version(version.id, version.versionNo, version.status.name(),
                version.draftRevision, "/api/floor-plan/versions/" + version.id + "/svg", viewBox(version),
                seatResponses);
    }

    private static FloorPlanResponse.ViewBox viewBox(FloorPlanVersionEntity version) {
        return new FloorPlanResponse.ViewBox(version.viewBoxMinX, version.viewBoxMinY, version.viewBoxWidth,
                version.viewBoxHeight);
    }

    private FloorPlanEntity requirePlanForUpdate() {
        return plans.findSingletonForUpdate().orElseThrow(() ->
                ApplicationProblem.notFound("FLOOR_PLAN_NOT_FOUND", "No floor plan exists."));
    }

    private FloorPlanVersionEntity requireDraft(FloorPlanEntity plan) {
        return versions.findDraft(plan.id).orElseThrow(() ->
                ApplicationProblem.notFound("FLOOR_PLAN_DRAFT_NOT_FOUND", "No floor-plan draft exists."));
    }

    private FloorPlanVersionSeatEntity requireMarker(FloorPlanVersionEntity draft, long seatId) {
        return markers.findForVersionAndSeat(draft.id, seatId).orElseThrow(() ->
                ApplicationProblem.notFound("FLOOR_PLAN_SEAT_NOT_FOUND", "The seat is not in this draft."));
    }

    private static void checkRevision(FloorPlanVersionEntity draft, long revision) {
        if (draft.draftRevision != revision) {
            stale();
        }
    }

    private static void stale() {
        throw ApplicationProblem.conflict("STALE_FLOOR_PLAN", "The floor plan changed", "Refresh before retrying.");
    }

    private int nextDraftNumber(FloorPlanEntity plan, FloorPlanVersionEntity draft) {
        return Math.max(plan.permanentNumberWatermark,
                markers.findForVersion(draft.id).stream().mapToInt(value -> value.seatNumber).max().orElse(0)) + 1;
    }

    private void validateFutureReservations(int capacity) {
        var today = java.time.LocalDate.now(clock.withZone(configuration.timeZone()));
        boolean invalid = reservations.findFromForUpdate(today).stream()
                .filter(value -> rounds.findForTargetDate(value.targetDate).isEmpty())
                .anyMatch(value -> value.reservedSeatCount > capacity);
        if (invalid) {
            throw ApplicationProblem.conflict("FLOOR_PLAN_RESERVATION_CONFLICT", "Floor plan conflicts with reservations",
                    "A future reservation exceeds the capacity that would apply after this change.");
        }
    }

    public record UploadResult(boolean created, AdminFloorPlanResponse response) {
    }

    private record SubmittedPreference(long seatId, int stars) {
    }
}