package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class BidRepository implements PanacheRepositoryBase<BidEntity, Long> {

    public List<BidEntity> findForParticipation(long participationId) {
        return list("participation.id", participationId);
    }

    public List<BidEntity> findForDate(long roundDateId) {
        return list("roundDate.id = ?1 order by tokens desc", roundDateId);
    }

    public List<BidEntity> findForRound(long roundId) {
        return list("roundDate.round.id", roundId);
    }

    public boolean hasPositiveBid(long roundId, long employeeId) {
        return count("roundDate.round.id = ?1 and participation.employee.id = ?2", roundId, employeeId) > 0;
    }

}