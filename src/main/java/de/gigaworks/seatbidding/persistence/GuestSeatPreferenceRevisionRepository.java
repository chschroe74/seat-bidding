package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class GuestSeatPreferenceRevisionRepository
        implements PanacheRepositoryBase<GuestSeatPreferenceRevisionEntity, Long> {

    public Optional<GuestSeatPreferenceRevisionEntity> findLatest(long planId) {
        return find("floorPlan.id = ?1 order by revisionNo desc", planId).firstResultOptional();
    }

    public Optional<GuestSeatPreferenceRevisionEntity> findAtCutoff(long planId, Instant cutoff) {
        return find("floorPlan.id = ?1 and createdAt < ?2 order by revisionNo desc", planId, cutoff)
                .firstResultOptional();
    }
}