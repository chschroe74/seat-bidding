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
    
}

