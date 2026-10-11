package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class EmployeeSeatPreferenceRevisionRepository
        implements PanacheRepositoryBase<EmployeeSeatPreferenceRevisionEntity, Long> {

    public Optional<EmployeeSeatPreferenceRevisionEntity> findLatest(long employeeId, long planId) {
        return find("employee.id = ?1 and floorPlan.id = ?2 order by revisionNo desc", employeeId, planId)
                .firstResultOptional();
    }

    public Optional<EmployeeSeatPreferenceRevisionEntity> findLatestForUpdate(long employeeId, long planId) {
        return find("employee.id = ?1 and floorPlan.id = ?2 order by revisionNo desc", employeeId, planId)
                .withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    public Optional<EmployeeSeatPreferenceRevisionEntity> findAtCutoff(long employeeId, long planId, Instant cutoff) {
        return find("employee.id = ?1 and floorPlan.id = ?2 and createdAt < ?3 order by revisionNo desc",
                employeeId, planId, cutoff).firstResultOptional();
    }
}