package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PhysicalSeatRepository implements PanacheRepositoryBase<PhysicalSeatEntity, Long> {
}