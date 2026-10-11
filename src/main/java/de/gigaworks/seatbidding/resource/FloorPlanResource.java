package de.gigaworks.seatbidding.resource;

import de.gigaworks.seatbidding.dto.FloorPlanResponse;
import de.gigaworks.seatbidding.dto.ReplaceSeatPreferencesRequest;

import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

@Path("/api/floor-plan")
@SecurityRequirement(name = "formCookie")
public interface FloorPlanResource {

    @GET
    @Path("/current")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get the current floor plan and current employee's private preferences")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "404")
    FloorPlanResponse current();

    @PUT
    @Path("/current/preferences")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Atomically replace the current employee's private seat preferences")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "400")
    @APIResponse(responseCode = "409")
    FloorPlanResponse replacePreferences(@Valid ReplaceSeatPreferencesRequest request,
            @HeaderParam("X-CSRF-TOKEN") String csrfToken);

    @GET
    @Path("/versions/{versionId}/svg")
    @Produces("image/svg+xml")
    @Operation(summary = "Get an authorized immutable sanitized floor-plan SVG")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "304")
    @APIResponse(responseCode = "404")
    Response svg(@PathParam("versionId") long versionId, @HeaderParam("If-None-Match") String ifNoneMatch);
}