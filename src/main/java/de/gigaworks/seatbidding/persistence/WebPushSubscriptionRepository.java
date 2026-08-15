package de.gigaworks.seatbidding.persistence;

import de.gigaworks.seatbidding.notification.PushSubscriptionStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class WebPushSubscriptionRepository implements PanacheRepositoryBase<WebPushSubscriptionEntity, Long> {

    public Optional<WebPushSubscriptionEntity> findByEndpointHash(String endpointHash) {
        return find("endpointHash", endpointHash).firstResultOptional();
    }

    public Optional<WebPushSubscriptionEntity> findActiveOwned(long id, long employeeId) {
        return find("id = ?1 and employee.id = ?2 and status = ?3", id, employeeId, PushSubscriptionStatus.ACTIVE)
                .firstResultOptional();
    }

    public List<WebPushSubscriptionEntity> findActiveForEmployee(long employeeId, Instant now) {
        return list("employee.id = ?1 and status = ?2 and (expiresAt is null or expiresAt > ?3) order by id",
                employeeId, PushSubscriptionStatus.ACTIVE, now);
    }

    public List<WebPushSubscriptionEntity> findActiveForEmployees(Collection<Long> employeeIds, Instant now) {
        if (employeeIds.isEmpty()) {
            return List.of();
        }
        return list("employee.id in ?1 and status = ?2 and (expiresAt is null or expiresAt > ?3) order by employee.id, id",
                employeeIds, PushSubscriptionStatus.ACTIVE, now);
    }

}