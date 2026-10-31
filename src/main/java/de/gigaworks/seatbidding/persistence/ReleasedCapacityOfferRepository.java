package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class ReleasedCapacityOfferRepository implements PanacheRepositoryBase<ReleasedCapacityOfferEntity, Long> {

    public List<ReleasedCapacityOfferEntity> findActiveForDateForUpdate(long roundDateId) {
        return find("roundDate.id = ?1 and status = ?2 order by deadline, id", roundDateId, OfferStatus.ACTIVE)
                .withLock(LockModeType.PESSIMISTIC_WRITE).list();
    }

    public boolean hasBlockingOffer(long roundDateId, AttendancePeriod period, Instant now) {
        return count("roundDate.id = ?1 and status = ?2 and deadline > ?3 and "
                + "(attendancePeriod = ?4 or attendancePeriod = ?5 or ?4 = ?5)",
                roundDateId, OfferStatus.ACTIVE, now, period, AttendancePeriod.FULL_DAY) > 0;
    }
}