package de.gigaworks.seatbidding.resource;

import de.gigaworks.seatbidding.dto.MeResponse;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

@Path("/api/me")
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "formCookie")
public interface MeResource {
    
    @GET
    @Operation(summary = "Get the authenticated employee profile")
    @APIResponse(responseCode = "200")
    MeResponse get();
    
}
