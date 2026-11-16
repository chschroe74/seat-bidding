package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class RelinquishmentCreditRepository implements PanacheRepositoryBase<RelinquishmentCreditEntity, Long> {

    public List<RelinquishmentCreditEntity> findSpendableForUpdate(long employeeId, long roundId, Instant now) {
        return find("employee.id = ?1 and round.id = ?2 and remainingTokens > 0 and expiresAt > ?3 "
                + "and expiredAt is null order by expiresAt, id", employeeId, roundId, now)
                .withLock(LockModeType.PESSIMISTIC_WRITE).list();
    }

    public int available(long employeeId, long roundId, Instant now) {
        return getEntityManager().createQuery("select coalesce(sum(c.remainingTokens), 0) from RelinquishmentCreditEntity c "
                + "where c.employee.id = :employee and c.round.id = :round and c.expiresAt > :now "
                + "and c.expiredAt is null", Long.class)
                .setParameter("employee", employeeId).setParameter("round", roundId).setParameter("now", now)
                .getSingleResult().intValue();
    }

    public Optional<RelinquishmentCreditEntity> findForAssignment(long assignmentId) {
        return find("seatAssignment.id", assignmentId).firstResultOptional();
    }

    public Optional<RelinquishmentCreditEntity> findByIdForUpdate(long id) {
        return find("id", id).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    public List<RelinquishmentCreditEntity> findExpiredForUpdate(long employeeId, long roundId, Instant now) {
        return find("employee.id = ?1 and round.id = ?2 and remainingTokens > 0 and expiresAt <= ?3 "
                + "and expiredAt is null order by id", employeeId, roundId, now)
                .withLock(LockModeType.PESSIMISTIC_WRITE).list();
    }
}