package de.gigaworks.seatbidding.resource;

import de.gigaworks.seatbidding.dto.AssignmentsResponse;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

@Path("/api/assignments/latest")
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "formCookie")
public interface AssignmentsResource {
    
    @GET
    @Operation(summary = "Get the latest published assignments")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "404")
    AssignmentsResponse get();
    
}
