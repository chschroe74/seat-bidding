package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BidReminderSuppressionRepository implements PanacheRepositoryBase<BidReminderSuppressionEntity, Long> {

    public boolean exists(long roundId, long employeeId) {
        return count("round.id = ?1 and employee.id = ?2", roundId, employeeId) > 0;
    }

}