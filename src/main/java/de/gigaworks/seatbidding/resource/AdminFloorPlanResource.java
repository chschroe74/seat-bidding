package de.gigaworks.seatbidding.resource;

import de.gigaworks.seatbidding.dto.AdminFloorPlanResponse;
import de.gigaworks.seatbidding.dto.ConfirmedRevisionRequest;
import de.gigaworks.seatbidding.dto.FloorPlanSeatMutationRequest;
import de.gigaworks.seatbidding.dto.GuestSeatPreferencesResponse;
import de.gigaworks.seatbidding.dto.ReplaceGuestSeatPreferencesRequest;
import de.gigaworks.seatbidding.dto.RevisionRequest;

import org.jboss.resteasy.reactive.RestForm;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/admin/floor-plan")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@SecurityRequirement(name = "formCookie")
public interface AdminFloorPlanResource {

    @GET
    @Operation(summary = "Get floor-plan administration state")
    @APIResponse(responseCode = "200")
    AdminFloorPlanResponse get();

    @GET
    @Path("/current/guest-preferences")
    @Operation(summary = "Get the administrator-only anonymous guest seat preference profile")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "404")
    GuestSeatPreferencesResponse guestPreferences();

    @PUT
    @Path("/current/guest-preferences")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Atomically replace the anonymous guest seat preference profile")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "400")
    @APIResponse(responseCode = "409")
    GuestSeatPreferencesResponse replaceGuestPreferences(@Valid ReplaceGuestSeatPreferencesRequest request,
            @HeaderParam("X-CSRF-TOKEN") String csrfToken);

    @POST
    @Path("/draft/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Upload and sanitize a floor-plan SVG draft")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "201")
    @APIResponse(responseCode = "400")
    @APIResponse(responseCode = "413")
    @APIResponse(responseCode = "415")
    Response upload(@RestForm("file") FileUpload file, @HeaderParam("X-CSRF-TOKEN") String csrfToken);

    @POST
    @Path("/draft/seats")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Add a provisionally numbered seat to the draft")
    @APIResponse(responseCode = "201")
    @APIResponse(responseCode = "409")
    Response addSeat(@Valid FloorPlanSeatMutationRequest request,
            @HeaderParam("X-CSRF-TOKEN") String csrfToken);

    @PUT
    @Path("/draft/seats/{seatId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Move a draft seat marker")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "409")
    AdminFloorPlanResponse moveSeat(@PathParam("seatId") long seatId, @Valid FloorPlanSeatMutationRequest request,
            @HeaderParam("X-CSRF-TOKEN") String csrfToken);

    @DELETE
    @Path("/draft/seats/{seatId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Remove or retire a draft seat")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "409")
    AdminFloorPlanResponse deleteSeat(@PathParam("seatId") long seatId, RevisionRequest request,
            @HeaderParam("X-CSRF-TOKEN") String csrfToken);

    @POST
    @Path("/draft/seats/{seatId}/restore")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Restore an inherited seat pending removal")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "409")
    AdminFloorPlanResponse restoreSeat(@PathParam("seatId") long seatId, RevisionRequest request,
            @HeaderParam("X-CSRF-TOKEN") String csrfToken);

    @POST
    @Path("/draft/remove-all-seats")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Remove every seat from the draft")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "409")
    AdminFloorPlanResponse removeAll(ConfirmedRevisionRequest request,
            @HeaderParam("X-CSRF-TOKEN") String csrfToken);

    @POST
    @Path("/draft/publish")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Publish the floor-plan draft for the next successor round")
    @APIResponse(responseCode = "201")
    @APIResponse(responseCode = "409")
    Response publish(RevisionRequest request, @HeaderParam("X-CSRF-TOKEN") String csrfToken);

    @DELETE
    @Path("/draft")
    @Operation(summary = "Discard the mutable floor-plan draft")
    @APIResponse(responseCode = "204")
    Response discard(@HeaderParam("X-CSRF-TOKEN") String csrfToken);

    @POST
    @Path("/deactivation")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Schedule floor-plan deactivation for the next successor round")
    @APIResponse(responseCode = "202")
    @APIResponse(responseCode = "409")
    Response deactivate(ConfirmedRevisionRequest request, @HeaderParam("X-CSRF-TOKEN") String csrfToken);
}