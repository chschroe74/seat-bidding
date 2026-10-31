package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;

@ApplicationScoped
public class RoundDateRepository implements PanacheRepositoryBase<RoundDateEntity, Long> {
    
    public List<RoundDateEntity> findForRound(long roundId) {
        return list("round.id = ?1 order by ordinal", roundId);
    }

    public Optional<RoundDateEntity> findForRoundAndDate(long roundId, LocalDate date) {
        return find("round.id = ?1 and targetDate = ?2", roundId, date).firstResultOptional();
    }

}