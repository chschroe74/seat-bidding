package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.util.Optional;

@ApplicationScoped
public class FloorPlanRepository implements PanacheRepositoryBase<FloorPlanEntity, Long> {

    public Optional<FloorPlanEntity> findSingleton() {
        return findAll().firstResultOptional();
    }

    public Optional<FloorPlanEntity> findSingletonForUpdate() {
        return findAll().withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }
}