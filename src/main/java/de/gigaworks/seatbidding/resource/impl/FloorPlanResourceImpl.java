package de.gigaworks.seatbidding.resource.impl;

import de.gigaworks.seatbidding.dto.FloorPlanResponse;
import de.gigaworks.seatbidding.dto.ReplaceSeatPreferencesRequest;
import de.gigaworks.seatbidding.floorplan.FloorPlanService;
import de.gigaworks.seatbidding.resource.FloorPlanResource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class FloorPlanResourceImpl implements FloorPlanResource {

    @Inject FloorPlanService service;

    @Override public FloorPlanResponse current() { return service.current(); }

    @Override public FloorPlanResponse replacePreferences(ReplaceSeatPreferencesRequest request, String csrfToken) {
        return service.replacePreferences(request);
    }

    @Override
    public Response svg(long versionId, String ifNoneMatch) {
        var version = service.authorizedVersion(versionId);
        var tag = new EntityTag(version.contentSha256);
        if (tag.toString().equals(ifNoneMatch)) {
            return Response.notModified(tag).header("Cache-Control", "private, no-cache").build();
        }
        return Response.ok(version.sanitizedSvg, "image/svg+xml").tag(tag)
                .header("Cache-Control", "private, no-cache")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
                .header("X-Content-Type-Options", "nosniff").build();
    }
}