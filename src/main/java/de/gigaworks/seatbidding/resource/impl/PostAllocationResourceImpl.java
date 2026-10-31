package de.gigaworks.seatbidding.resource.impl;

import de.gigaworks.seatbidding.allocation.AssignmentQueryService;
import de.gigaworks.seatbidding.dto.AssignmentsResponse;
import de.gigaworks.seatbidding.dto.LateSeatSelectionRequest;
import de.gigaworks.seatbidding.dto.OpenBookingRequest;
import de.gigaworks.seatbidding.dto.SeatAvailabilityResponse;
import de.gigaworks.seatbidding.exception.ApplicationProblem;
import de.gigaworks.seatbidding.occupancy.PostAllocationService;
import de.gigaworks.seatbidding.persistence.AttendancePeriod;
import de.gigaworks.seatbidding.resource.PostAllocationResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.LocalDate;

@ApplicationScoped
public class PostAllocationResourceImpl implements PostAllocationResource {

    @Inject PostAllocationService service;
    @Inject AssignmentQueryService assignments;

    @Override
    public SeatAvailabilityResponse availability(long roundId, LocalDate date, AttendancePeriod attendancePeriod) {
        return service.availability(roundId, date, attendancePeriod);
    }

    @Override
    public AssignmentsResponse.AssignmentDay relinquish(long roundId, LocalDate date, long occupancyId,
            String csrfToken) {
        service.relinquish(roundId, date, occupancyId);
        return assignments.day(roundId, date);
    }

    @Override
    public AssignmentsResponse.AssignmentDay claim(long roundId, LocalDate date, long offerId, long candidateId,
            LateSeatSelectionRequest request, String csrfToken) {
        var result = service.claim(roundId, date, offerId, candidateId,
                request == null ? null : request.physicalSeatId());
        switch (result) {
            case CLAIMED -> { }
            case CAPACITY_TAKEN -> throw ApplicationProblem.conflict("CAPACITY_TAKEN", "Capacity unavailable",
                    "Compatible capacity was claimed by another employee.");
            case INSUFFICIENT_FUNDS -> throw ApplicationProblem.conflict("INSUFFICIENT_POST_ALLOCATION_FUNDS",
                    "Insufficient tokens", "The candidate was automatically declined because the price is no longer affordable.");
            case EXPIRED -> throw ApplicationProblem.conflict("OFFER_EXPIRED", "Offer expired",
                    "The offer deadline has passed.");
        }
        return assignments.day(roundId, date);
    }

    @Override
    public AssignmentsResponse.AssignmentDay decline(long roundId, LocalDate date, long offerId, long candidateId,
            String csrfToken) {
        service.decline(roundId, date, offerId, candidateId);
        return assignments.day(roundId, date);
    }

    @Override
    public Response book(long roundId, LocalDate date, OpenBookingRequest request, String csrfToken) {
        long bookingId = service.book(roundId, date, request.attendancePeriod(), request.physicalSeatId());
        return Response.created(URI.create("/api/assignments/" + roundId + "/dates/" + date
                + "/bookings/" + bookingId)).entity(assignments.day(roundId, date)).build();
    }
}