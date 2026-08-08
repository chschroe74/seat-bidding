package de.gigaworks.seatbidding.resource;

import de.gigaworks.seatbidding.dto.PublicConfigurationResponse;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

@Path("/api/public/configuration")
@Produces(MediaType.APPLICATION_JSON)
public interface PublicConfigurationResource {
    
    @GET
    @Operation(summary = "Get public client configuration")
    @APIResponse(responseCode = "200")
    PublicConfigurationResponse get();
    
}

