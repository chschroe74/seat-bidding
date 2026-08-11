package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

@ApplicationScoped
public class BiddingRoundRepository implements PanacheRepositoryBase<BiddingRoundEntity, Long> {
    
    public Optional<BiddingRoundEntity> findOpen() {
        return find("status", RoundStatus.OPEN).firstResultOptional();
    }
    
    public Optional<BiddingRoundEntity> findDueForUpdate(Instant now) {
        return find("status = ?1 and cutoffAt <= ?2", RoundStatus.OPEN, now)
                .withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }
    
    public Optional<BiddingRoundEntity> findLatestCompleted() {
        return find("status = ?1 order by sequenceNo desc", RoundStatus.COMPLETED).firstResultOptional();
    }
    
    public Optional<BiddingRoundEntity> findForTargetDate(LocalDate targetDate) {
        return find("select date.round from RoundDateEntity date where date.targetDate = ?1", targetDate)
                .firstResultOptional();
    }
    
    public Optional<BiddingRoundEntity> findForTargetDateForUpdate(LocalDate targetDate) {
        return find("select date.round from RoundDateEntity date where date.targetDate = ?1", targetDate)
                .withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }
    
}
