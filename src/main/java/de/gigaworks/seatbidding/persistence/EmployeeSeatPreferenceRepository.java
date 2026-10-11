package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class EmployeeSeatPreferenceRepository implements PanacheRepositoryBase<EmployeeSeatPreferenceEntity, Long> {

    public List<EmployeeSeatPreferenceEntity> findForRevision(long revisionId) {
        return list("revision.id", revisionId);
    }
}