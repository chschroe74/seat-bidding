package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class FloorPlanVersionRepository implements PanacheRepositoryBase<FloorPlanVersionEntity, Long> {

    public Optional<FloorPlanVersionEntity> findDraft(long planId) {
        return find("floorPlan.id = ?1 and status = ?2", planId, FloorPlanVersionStatus.DRAFT).firstResultOptional();
    }

    public long nextVersion(long planId) {
        Long value = getEntityManager().createQuery(
                "select max(v.versionNo) from FloorPlanVersionEntity v where v.floorPlan.id = :planId", Long.class)
                .setParameter("planId", planId).getSingleResult();
        return value == null ? 1 : value + 1;
    }

    public Optional<FloorPlanVersionEntity> findLatestPublished(long planId) {
        return find("floorPlan.id = ?1 and status = ?2 order by versionNo desc", planId,
                FloorPlanVersionStatus.PUBLISHED).firstResultOptional();
    }
}