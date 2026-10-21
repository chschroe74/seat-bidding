package de.gigaworks.seatbidding.resource.impl;

import de.gigaworks.seatbidding.dto.AdminFloorPlanResponse;
import de.gigaworks.seatbidding.dto.ConfirmedRevisionRequest;
import de.gigaworks.seatbidding.dto.FloorPlanSeatMutationRequest;
import de.gigaworks.seatbidding.dto.GuestSeatPreferencesResponse;
import de.gigaworks.seatbidding.dto.ReplaceGuestSeatPreferencesRequest;
import de.gigaworks.seatbidding.dto.RevisionRequest;
import de.gigaworks.seatbidding.exception.ApplicationProblem;
import de.gigaworks.seatbidding.floorplan.FloorPlanService;
import de.gigaworks.seatbidding.resource.AdminFloorPlanResource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;

import org.jboss.resteasy.reactive.multipart.FileUpload;

@ApplicationScoped
public class AdminFloorPlanResourceImpl implements AdminFloorPlanResource {

    @Inject FloorPlanService service;

    @Override
    public AdminFloorPlanResponse get() {
        return service.adminState();
    }

    @Override public GuestSeatPreferencesResponse guestPreferences() { return service.guestPreferences(); }
    @Override public GuestSeatPreferencesResponse replaceGuestPreferences(ReplaceGuestSeatPreferencesRequest request,
            String csrfToken) { return service.replaceGuestPreferences(request); }

    @Override
    public Response upload(FileUpload file, String csrfToken) {
        if (file == null || file.uploadedFile() == null) {
            throw ApplicationProblem.badRequest("SVG_REQUIRED", "SVG required", "Select an SVG file to upload.");
        }
        String contentType = file.contentType();
        if (contentType != null && !(contentType.contains("svg") || contentType.contains("xml")
                || contentType.equals("application/octet-stream"))) {
            throw new ApplicationProblem(415, "SVG_MEDIA_TYPE_REQUIRED", "Unsupported floor-plan format",
                    "Upload an SVG file.");
        }
        try {
            var result = service.upload(Files.readAllBytes(file.uploadedFile()));
            return Response.status(result.created() ? Response.Status.CREATED : Response.Status.OK)
                    .entity(result.response()).build();
        }
        catch (IOException exception) {
            throw ApplicationProblem.badRequest("INVALID_SVG", "Invalid floor plan", "The upload could not be read.");
        }
    }

    @Override public Response addSeat(FloorPlanSeatMutationRequest request, String csrfToken) {
        return Response.status(Response.Status.CREATED).entity(service.addSeat(request)).build();
    }
    @Override public AdminFloorPlanResponse moveSeat(long seatId, FloorPlanSeatMutationRequest request,
            String csrfToken) { return service.moveSeat(seatId, request); }
    @Override public AdminFloorPlanResponse deleteSeat(long seatId, RevisionRequest request, String csrfToken) {
        return service.deleteSeat(seatId, request);
    }
    @Override public AdminFloorPlanResponse restoreSeat(long seatId, RevisionRequest request, String csrfToken) {
        return service.restoreSeat(seatId, request);
    }
    @Override public AdminFloorPlanResponse removeAll(ConfirmedRevisionRequest request, String csrfToken) {
        return service.removeAll(request);
    }
    @Override public Response publish(RevisionRequest request, String csrfToken) {
        return Response.status(Response.Status.CREATED).entity(service.publish(request)).build();
    }
    @Override public Response discard(String csrfToken) {
        service.discardDraft();
        return Response.noContent().build();
    }
    @Override public Response deactivate(ConfirmedRevisionRequest request, String csrfToken) {
        return Response.accepted(service.deactivate(request)).build();
    }
}