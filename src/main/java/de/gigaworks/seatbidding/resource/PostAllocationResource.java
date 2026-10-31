package de.gigaworks.seatbidding.resource;

import de.gigaworks.seatbidding.dto.AssignmentsResponse;
import de.gigaworks.seatbidding.dto.LateSeatSelectionRequest;
import de.gigaworks.seatbidding.dto.OpenBookingRequest;
import de.gigaworks.seatbidding.dto.SeatAvailabilityResponse;
import de.gigaworks.seatbidding.persistence.AttendancePeriod;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

@Path("/api/assignments/{roundId}/dates/{date}")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("USER")
@SecurityRequirement(name = "formCookie")
public interface PostAllocationResource {

    @GET
    @Path("/seat-availability")
    @Operation(summary = "List action-scoped compatible numbered seats without occupant disclosure")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "404", description = "No floor plan or permitted action")
    SeatAvailabilityResponse availability(@PathParam("roundId") long roundId,
            @PathParam("date") LocalDate date, @QueryParam("attendancePeriod") AttendancePeriod attendancePeriod);

    @POST
    @Path("/occupancies/{occupancyId}/relinquishment")
    @Operation(summary = "Irreversibly relinquish the current employee's active occupancy")
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true)
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "404")
    @APIResponse(responseCode = "409")
    AssignmentsResponse.AssignmentDay relinquish(@PathParam("roundId") long roundId,
            @PathParam("date") LocalDate date, @PathParam("occupancyId") long occupancyId,
            @HeaderParam("X-CSRF-TOKEN") String csrfToken);

    @POST
    @Path("/offers/{offerId}/candidates/{candidateId}/claim")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.WILDCARD})
    @Operation(summary = "Claim a pending waiting-list offer using an explicit seat in floor-plan mode")
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true)
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "409", description = "Offer, funds, capacity, or selected seat changed")
    AssignmentsResponse.AssignmentDay claim(@PathParam("roundId") long roundId,
            @PathParam("date") LocalDate date, @PathParam("offerId") long offerId,
            @PathParam("candidateId") long candidateId, LateSeatSelectionRequest request,
            @HeaderParam("X-CSRF-TOKEN") String csrfToken);

    @POST
    @Path("/offers/{offerId}/candidates/{candidateId}/decline")
    @Operation(summary = "Permanently decline a pending waiting-list offer")
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true)
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "409")
    AssignmentsResponse.AssignmentDay decline(@PathParam("roundId") long roundId,
            @PathParam("date") LocalDate date, @PathParam("offerId") long offerId,
            @PathParam("candidateId") long candidateId,
            @HeaderParam("X-CSRF-TOKEN") String csrfToken);

    @POST
    @Path("/bookings")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create an open booking with authoritative price and credit-first funding")
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true)
    @APIResponse(responseCode = "201")
    @APIResponse(responseCode = "409", description = "Eligibility, funds, capacity, or selected seat changed")
    Response book(@PathParam("roundId") long roundId, @PathParam("date") LocalDate date,
            @Valid OpenBookingRequest request, @HeaderParam("X-CSRF-TOKEN") String csrfToken);
}