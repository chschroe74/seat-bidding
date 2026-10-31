package de.gigaworks.seatbidding.resource;

import de.gigaworks.seatbidding.dto.FloorPlanResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

@Path("/api/assignments")
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "formCookie")
public interface AssignmentFloorPlanResource {

    @GET
    @Path("/latest/floor-plan")
    @Operation(summary = "Get the numbered floor plan for the latest published assignments")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "404")
    FloorPlanResponse get();

    @GET
    @Path("/{roundId}/floor-plan")
    @Operation(summary = "Get the immutable numbered floor plan for a still-addressable completed round")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "404")
    FloorPlanResponse getRound(@PathParam("roundId") long roundId);
}