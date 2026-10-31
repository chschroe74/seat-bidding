package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.util.Optional;

@ApplicationScoped
public class RoundDateCapacityStateRepository implements PanacheRepositoryBase<RoundDateCapacityStateEntity, Long> {

    public Optional<RoundDateCapacityStateEntity> findForDate(long roundDateId) {
        return find("roundDate.id", roundDateId).firstResultOptional();
    }

    public Optional<RoundDateCapacityStateEntity> findForDateForUpdate(long roundDateId) {
        return find("roundDate.id", roundDateId).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }
}