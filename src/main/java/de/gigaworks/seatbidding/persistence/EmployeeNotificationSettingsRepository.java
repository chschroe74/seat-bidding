package de.gigaworks.seatbidding.persistence;

import de.gigaworks.seatbidding.notification.ReminderStartWeekday;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class EmployeeNotificationSettingsRepository
        implements PanacheRepositoryBase<EmployeeNotificationSettingsEntity, Long> {

    public Optional<EmployeeNotificationSettingsEntity> findForEmployee(long employeeId) {
        return find("employee.id", employeeId).firstResultOptional();
    }

    public List<EmployeeEntity> findEligibleEmployees(long roundId, List<ReminderStartWeekday> weekdays,
            java.time.Instant now) {
        return getEntityManager().createQuery("""
                select settings.employee
                  from EmployeeNotificationSettingsEntity settings
                 where settings.bidRemindersEnabled = true
                   and settings.bidReminderStartWeekday in :weekdays
                   and settings.employee.enabled = true
                   and exists (
                       select subscription.id from WebPushSubscriptionEntity subscription
                        where subscription.employee = settings.employee
                          and subscription.status = de.gigaworks.seatbidding.notification.PushSubscriptionStatus.ACTIVE
                          and (subscription.expiresAt is null or subscription.expiresAt > :now))
                   and not exists (
                       select suppression.id from BidReminderSuppressionEntity suppression
                        where suppression.employee = settings.employee and suppression.round.id = :roundId)
                   and not exists (
                       select bid.id from BidEntity bid
                        where bid.participation.employee = settings.employee and bid.roundDate.round.id = :roundId)
                 order by settings.employee.id
                """, EmployeeEntity.class)
                .setParameter("weekdays", weekdays)
                .setParameter("roundId", roundId)
                .setParameter("now", now)
                .getResultList();
    }

}