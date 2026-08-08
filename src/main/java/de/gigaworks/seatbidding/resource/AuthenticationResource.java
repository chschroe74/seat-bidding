package de.gigaworks.seatbidding.resource;

import de.gigaworks.seatbidding.dto.ActivationResendRequest;
import de.gigaworks.seatbidding.dto.ActivationVerifyRequest;
import de.gigaworks.seatbidding.dto.ActivationVerifyResponse;
import de.gigaworks.seatbidding.dto.AuthenticationStartRequest;
import de.gigaworks.seatbidding.dto.AuthenticationStartResponse;
import de.gigaworks.seatbidding.dto.CreatePasswordRequest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface AuthenticationResource {
    
    @POST
    @Path("/start")
    @Operation(summary = "Start or resume email-first authentication")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "403")
    @APIResponse(responseCode = "429")
    AuthenticationStartResponse start(@Valid AuthenticationStartRequest request);
    
    @POST
    @Path("/activation/resend")
    @Operation(summary = "Send a replacement activation code")
    @APIResponse(responseCode = "202")
    @APIResponse(responseCode = "429")
    Response resend(@Valid ActivationResendRequest request);
    
    @POST
    @Path("/activation/verify")
    @Operation(summary = "Verify a six-digit activation code")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "400")
    @APIResponse(responseCode = "429")
    ActivationVerifyResponse verify(@Valid ActivationVerifyRequest request);
    
    @POST
    @Path("/activation/password")
    @Operation(summary = "Create a first-time password")
    @APIResponse(responseCode = "204")
    @APIResponse(responseCode = "400")
    Response createPassword(@Valid CreatePasswordRequest request);
    
    @GET
    @Path("/csrf")
    @Operation(summary = "Initialize the signed CSRF cookie")
    @APIResponse(responseCode = "204")
    Response csrf();
    
    @POST
    @Path("/logout")
    @Consumes(MediaType.WILDCARD)
    @RolesAllowed("employee")
    @SecurityRequirement(name = "formCookie")
    @Operation(summary = "Expire the current form-authentication cookie")
    @APIResponse(responseCode = "204")
    Response logout(@HeaderParam("X-CSRF-Token")
    @Parameter(name = "X-CSRF-Token", description = "Required for web cookie sessions") String ignored);
    
}
