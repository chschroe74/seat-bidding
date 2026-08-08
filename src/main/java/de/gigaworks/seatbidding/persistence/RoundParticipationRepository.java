package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.time.Duration;

@ApplicationScoped
public class RoundParticipationRepository implements PanacheRepositoryBase<RoundParticipationEntity, Long> {
    
    public Optional<RoundParticipationEntity> findForUpdate(long roundId, long employeeId, Duration timeout) {
        return getEntityManager().createQuery(
                        "from RoundParticipationEntity p where p.round.id = :roundId and p.employee.id = :employeeId",
                        RoundParticipationEntity.class)
                .setParameter("roundId", roundId)
                .setParameter("employeeId", employeeId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint("jakarta.persistence.lock.timeout", Math.toIntExact(timeout.toMillis()))
                .getResultStream().findFirst();
    }
    
    public List<RoundParticipationEntity> findForRound(long roundId) {
        return list("round.id", roundId);
    }
    
}
