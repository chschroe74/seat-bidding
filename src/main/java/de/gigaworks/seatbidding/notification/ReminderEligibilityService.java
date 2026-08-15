package de.gigaworks.seatbidding.notification;

import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.persistence.EmployeeNotificationSettingsRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ReminderEligibilityService {

    @Inject
    BiddingRoundRepository rounds;

    @Inject
    EmployeeNotificationSettingsRepository settings;

    @Transactional
    public Optional<Candidates> select(Instant scheduledFor) {
        var round = rounds.findOpen().orElse(null);
        if (round == null || !scheduledFor.isBefore(round.cutoffAt)) {
            return Optional.empty();
        }
        var zone = ZoneId.of(round.scheduleZone);
        LocalDate businessDate = scheduledFor.atZone(zone).toLocalDate();
        DayOfWeek weekday = businessDate.getDayOfWeek();
        if (weekday == DayOfWeek.SATURDAY || weekday == DayOfWeek.SUNDAY) {
            return Optional.empty();
        }
        var eligibleWeekdays = ReminderStartWeekday.eligibleOn(weekday);
        var employeeIds = settings.findEligibleEmployees(round.id, eligibleWeekdays, scheduledFor).stream()
                .map(employee -> employee.id).toList();
        return Optional.of(new Candidates(round.id, businessDate, scheduledFor, employeeIds));
    }

    public record Candidates(long roundId, LocalDate businessDate, Instant scheduledFor, List<Long> employeeIds) {
    }

}