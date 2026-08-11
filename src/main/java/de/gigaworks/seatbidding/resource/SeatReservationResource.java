package de.gigaworks.seatbidding.resource;

import de.gigaworks.seatbidding.dto.CreateSeatReservationRequest;
import de.gigaworks.seatbidding.dto.SeatReservationListResponse;

import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

@Path("/api/admin/seat-reservations")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@SecurityRequirement(name = "formCookie")
public interface SeatReservationResource {
    
    @GET
    @Operation(summary = "List seat reservations in an inclusive date range")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "400", description = "Invalid date range")
    @APIResponse(responseCode = "403", description = "Administrator authorization required")
    SeatReservationListResponse list(@QueryParam("from") String from, @QueryParam("to") String to);
    
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a seat reservation")
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true)
    @APIResponse(responseCode = "201")
    @APIResponse(responseCode = "400", description = "Invalid reservation")
    @APIResponse(responseCode = "403", description = "Administrator authorization required")
    @APIResponse(responseCode = "409", description = "Duplicate or immutable reservation")
    Response create(@Valid CreateSeatReservationRequest request,
            @HeaderParam("X-CSRF-TOKEN") String csrfToken);
    
    @DELETE
    @Path("/{reservationId}")
    @Operation(summary = "Delete a mutable seat reservation")
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true)
    @APIResponse(responseCode = "204")
    @APIResponse(responseCode = "403", description = "Administrator authorization required")
    @APIResponse(responseCode = "404", description = "Reservation not found")
    @APIResponse(responseCode = "409", description = "Reservation is immutable")
    Response delete(@PathParam("reservationId") long reservationId,
            @HeaderParam("X-CSRF-TOKEN") String csrfToken);
    
}
