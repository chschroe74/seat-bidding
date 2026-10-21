package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class GuestSeatPreferenceRepository implements PanacheRepositoryBase<GuestSeatPreferenceEntity, Long> {

    public List<GuestSeatPreferenceEntity> findForRevision(long revisionId) {
        return list("revision.id", revisionId);
    }
}