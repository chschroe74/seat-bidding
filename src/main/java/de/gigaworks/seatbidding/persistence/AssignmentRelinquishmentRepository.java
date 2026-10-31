package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class AssignmentRelinquishmentRepository implements PanacheRepositoryBase<AssignmentRelinquishmentEntity, Long> {

    public Optional<AssignmentRelinquishmentEntity> findForOccupancy(long occupancyId) {
        return find("occupancy.id", occupancyId).firstResultOptional();
    }
}