package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class SeatOfferCandidateRepository implements PanacheRepositoryBase<SeatOfferCandidateEntity, Long> {

    public Optional<SeatOfferCandidateEntity> findOwnedForUpdate(long offerId, long candidateId, long employeeId) {
        return find("id = ?1 and offer.id = ?2 and employee.id = ?3", candidateId, offerId, employeeId)
                .withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    public List<SeatOfferCandidateEntity> findForOffer(long offerId) {
        return list("offer.id = ?1 order by tierTokens desc, id", offerId);
    }

    public List<SeatOfferCandidateEntity> findForEmployeeDate(long employeeId, long roundDateId) {
        return list("employee.id = ?1 and roundDate.id = ?2 order by offeredAt, id", employeeId, roundDateId);
    }

    public boolean hasPending(long employeeId, long roundDateId, AttendancePeriod period) {
        return count("employee.id = ?1 and roundDate.id = ?2 and attendancePeriod = ?3 and status = ?4",
                employeeId, roundDateId, period, OfferCandidateStatus.PENDING) > 0;
    }

    public boolean hasAnyPending(long employeeId, long roundDateId) {
        return count("employee.id = ?1 and roundDate.id = ?2 and status = ?3",
                employeeId, roundDateId, OfferCandidateStatus.PENDING) > 0;
    }

    public long countPendingCompatible(long roundDateId, AttendancePeriod period) {
        if (period == AttendancePeriod.FULL_DAY) {
            return count("roundDate.id = ?1 and status = ?2", roundDateId, OfferCandidateStatus.PENDING);
        }
        return count("roundDate.id = ?1 and status = ?2 and (attendancePeriod = ?3 or attendancePeriod = ?4)",
                roundDateId, OfferCandidateStatus.PENDING, period, AttendancePeriod.FULL_DAY);
    }
}