package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class PostAllocationBookingCreditRepository implements PanacheRepositoryBase<PostAllocationBookingCreditEntity, Long> {

    public List<PostAllocationBookingCreditEntity> findForBooking(long bookingId) {
        return list("booking.id = ?1 order by credit.id", bookingId);
    }
}