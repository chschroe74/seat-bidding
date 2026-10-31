package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class SeatOccupancyRepository implements PanacheRepositoryBase<SeatOccupancyEntity, Long> {

    public List<SeatOccupancyEntity> findActiveForDate(long roundDateId) {
        return list("roundDate.id = ?1 and status = ?2 order by id", roundDateId, OccupancyStatus.ACTIVE);
    }

    public List<SeatOccupancyEntity> findActiveForDateForUpdate(long roundDateId) {
        return find("roundDate.id = ?1 and status = ?2 order by physicalSeat.id nulls first, id",
                roundDateId, OccupancyStatus.ACTIVE).withLock(LockModeType.PESSIMISTIC_WRITE).list();
    }

    public Optional<SeatOccupancyEntity> findOwnedActiveForUpdate(long occupancyId, long employeeId,
            long roundDateId) {
        return find("id = ?1 and employee.id = ?2 and roundDate.id = ?3 and status = ?4",
                occupancyId, employeeId, roundDateId, OccupancyStatus.ACTIVE)
                .withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    public boolean hasActive(long roundDateId, long employeeId) {
        return count("roundDate.id = ?1 and employee.id = ?2 and status = ?3",
                roundDateId, employeeId, OccupancyStatus.ACTIVE) > 0;
    }

    public Optional<SeatOccupancyEntity> findOriginal(long assignmentId) {
        return find("originalSeatAssignment.id = ?1 and sourceType = ?2", assignmentId,
                OccupancySourceType.ORIGINAL_ASSIGNMENT).firstResultOptional();
    }
}