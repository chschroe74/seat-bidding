package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FloorPlanVersionSeatRepository implements PanacheRepositoryBase<FloorPlanVersionSeatEntity, Long> {

    public List<FloorPlanVersionSeatEntity> findForVersion(long versionId) {
        return list("version.id = ?1 order by seatNumber", versionId);
    }

    public List<FloorPlanVersionSeatEntity> findActiveForVersion(long versionId) {
        return list("version.id = ?1 and state = ?2 order by seatNumber", versionId, FloorPlanSeatState.ACTIVE);
    }

    public long countActiveForVersion(long versionId) {
        return count("version.id = ?1 and state = ?2", versionId, FloorPlanSeatState.ACTIVE);
    }

    public Optional<FloorPlanVersionSeatEntity> findForVersionAndSeat(long versionId, long seatId) {
        return find("version.id = ?1 and physicalSeat.id = ?2", versionId, seatId).firstResultOptional();
    }
}