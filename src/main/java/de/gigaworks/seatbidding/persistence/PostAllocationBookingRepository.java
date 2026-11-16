package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.Optional;

@ApplicationScoped
public class PostAllocationBookingRepository implements PanacheRepositoryBase<PostAllocationBookingEntity, Long> {

    public Optional<PostAllocationBookingEntity> findForOccupancy(long occupancyId) {
        return find("occupancy.id", occupancyId).firstResultOptional();
    }

    public Optional<PostAllocationBookingEntity> findForOccupancyForUpdate(long occupancyId) {
        return find("occupancy.id", occupancyId).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }
}