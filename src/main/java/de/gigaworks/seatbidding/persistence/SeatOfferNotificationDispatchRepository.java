package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.util.Optional;

@ApplicationScoped
public class SeatOfferNotificationDispatchRepository
        implements PanacheRepositoryBase<SeatOfferNotificationDispatchEntity, Long> {

    public Optional<SeatOfferNotificationDispatchEntity> findForCandidateForUpdate(long candidateId) {
        return find("candidate.id", candidateId).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }
}