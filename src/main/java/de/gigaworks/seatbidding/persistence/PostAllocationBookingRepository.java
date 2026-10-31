package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PostAllocationBookingRepository implements PanacheRepositoryBase<PostAllocationBookingEntity, Long> {
}