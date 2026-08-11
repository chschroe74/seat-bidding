package de.gigaworks.seatbidding.resource.impl;

import de.gigaworks.seatbidding.dto.CreateSeatReservationRequest;
import de.gigaworks.seatbidding.dto.SeatReservationListResponse;
import de.gigaworks.seatbidding.reservation.SeatReservationService;
import de.gigaworks.seatbidding.resource.SeatReservationResource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class SeatReservationResourceImpl implements SeatReservationResource {
    
    @Inject
    SeatReservationService service;
    
    @Override
    public SeatReservationListResponse list(String from, String to) {
        return service.list(from, to);
    }
    
    @Override
    public Response create(CreateSeatReservationRequest request, String csrfToken) {
        var created = service.create(request);
        return Response.created(created.location()).entity(created.response()).build();
    }
    
    @Override
    public Response delete(long reservationId, String csrfToken) {
        service.delete(reservationId);
        return Response.noContent().build();
    }
    
}
