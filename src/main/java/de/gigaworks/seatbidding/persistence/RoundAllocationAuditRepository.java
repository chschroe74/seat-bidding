package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class RoundAllocationAuditRepository implements PanacheRepositoryBase<RoundAllocationAuditEntity, Long> {

    public Optional<RoundAllocationAuditEntity> findForRound(long roundId) {
        return find("round.id", roundId).firstResultOptional();
    }

}
