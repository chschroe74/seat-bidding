package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.Optional;

@ApplicationScoped
public class BidReminderDispatchRepository implements PanacheRepositoryBase<BidReminderDispatchEntity, Long> {

    public Optional<BidReminderDispatchEntity> findClaim(long roundId, long employeeId, LocalDate businessDate) {
        return find("round.id = ?1 and employee.id = ?2 and businessDate = ?3", roundId, employeeId, businessDate)
                .firstResultOptional();
    }

}