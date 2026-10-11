package de.gigaworks.seatbidding.resource.impl;

import de.gigaworks.seatbidding.dto.FloorPlanResponse;
import de.gigaworks.seatbidding.floorplan.FloorPlanService;
import de.gigaworks.seatbidding.resource.AssignmentFloorPlanResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AssignmentFloorPlanResourceImpl implements AssignmentFloorPlanResource {

    @Inject FloorPlanService service;

    @Override public FloorPlanResponse get() { return service.latestAssignments(); }
}