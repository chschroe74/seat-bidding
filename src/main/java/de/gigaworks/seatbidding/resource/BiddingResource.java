package de.gigaworks.seatbidding.resource;

import de.gigaworks.seatbidding.dto.BiddingContextResponse;
import de.gigaworks.seatbidding.dto.ReplaceBidsRequest;

import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

@Path("/api/bidding/current")
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "formCookie")
public interface BiddingResource {
    
    @GET
    @Operation(summary = "Get the current employee's private bidding context")
    @APIResponse(responseCode = "200")
    BiddingContextResponse get();
    
    @PUT
    @Path("/bids")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Atomically replace the current employee's complete bid set")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "400")
    @APIResponse(responseCode = "409")
    BiddingContextResponse replace(@Valid ReplaceBidsRequest request);
    
}
