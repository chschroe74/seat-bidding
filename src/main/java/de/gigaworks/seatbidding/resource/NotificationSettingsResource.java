package de.gigaworks.seatbidding.resource;

import de.gigaworks.seatbidding.dto.NotificationSettingsResponse;
import de.gigaworks.seatbidding.dto.RegisterPushDeviceRequest;
import de.gigaworks.seatbidding.dto.SuppressBidRemindersRequest;
import de.gigaworks.seatbidding.dto.UpdateNotificationSettingsRequest;

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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

@Path("/api/settings/notifications")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("USER")
@SecurityRequirement(name = "formCookie")
public interface NotificationSettingsResource {

    @GET
    @Operation(summary = "Get synchronized bid-reminder preferences and registered devices")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "401", description = "Authentication required")
    NotificationSettingsResponse get();

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Replace synchronized bid-reminder preferences")
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true)
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "400", description = "Invalid reminder preference")
    @APIResponse(responseCode = "403", description = "Valid CSRF proof required")
    NotificationSettingsResponse update(@Valid UpdateNotificationSettingsRequest request,
            @HeaderParam("X-CSRF-TOKEN") String csrfToken);

    @POST
    @Path("/devices")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Register or refresh the current browser Web Push subscription")
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true)
    @APIResponse(responseCode = "200", description = "Existing device refreshed")
    @APIResponse(responseCode = "201", description = "New device registered")
    @APIResponse(responseCode = "400", description = "Invalid subscription")
    @APIResponse(responseCode = "409", description = "Subscription belongs to another account")
    Response registerDevice(@Valid RegisterPushDeviceRequest request,
            @HeaderParam("X-CSRF-TOKEN") String csrfToken);

    @DELETE
    @Path("/devices/{deviceId}")
    @Operation(summary = "Remove one registered Web Push device")
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true)
    @APIResponse(responseCode = "204")
    @APIResponse(responseCode = "404", description = "Employee-owned active device not found")
    Response removeDevice(@PathParam("deviceId") long deviceId,
            @HeaderParam("X-CSRF-TOKEN") String csrfToken);

    @POST
    @Path("/bid-reminders/current-round/suppression")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Irreversibly suppress reminders for the current open round")
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true)
    @APIResponse(responseCode = "204")
    @APIResponse(responseCode = "409", description = "Round is stale, closed, or already satisfied")
    Response suppressCurrentRound(@Valid SuppressBidRemindersRequest request,
            @HeaderParam("X-CSRF-TOKEN") String csrfToken);

}