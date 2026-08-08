package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class RoundDateRepository implements PanacheRepositoryBase<RoundDateEntity, Long> {
    
    public List<RoundDateEntity> findForRound(long roundId) {
        return list("round.id = ?1 order by ordinal", roundId);
    }
    
}

