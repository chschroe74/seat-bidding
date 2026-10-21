package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class ReservedGuestSeatAssignmentRepository
        implements PanacheRepositoryBase<ReservedGuestSeatAssignmentEntity, Long> {

    public List<ReservedGuestSeatAssignmentEntity> findForRound(long roundId) {
        return list("roundDate.round.id = ?1 order by roundDate.ordinal, physicalSeat.seatNumber", roundId);
    }
}