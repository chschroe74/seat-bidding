package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class SeatAssignmentRepository implements PanacheRepositoryBase<SeatAssignmentEntity, Long> {
    
    public List<SeatAssignmentEntity> findForDate(long roundDateId) {
        return list("roundDate.id = ?1 order by finalRank", roundDateId);
    }
    
}

