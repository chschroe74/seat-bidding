package de.gigaworks.seatbidding.round;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import de.gigaworks.seatbidding.exception.ConfigurationException;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@ApplicationScoped
public class RoundSchedule {
    
    public Instant nextCutoff(String expression, ZoneId zone, Instant after) {
        try {
            var cron = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)).parse(expression);
            cron.validate();
            return ExecutionTime.forCron(cron).nextExecution(after.atZone(zone))
                    .orElseThrow(() -> new ConfigurationException(
                            "Round scheduler cron expression {} has no future execution", expression))
                    .toInstant();
        }
        catch (IllegalArgumentException exception) {
            throw new ConfigurationException(exception, "Invalid round scheduler cron expression {}", expression);
        }
    }
    
    public List<LocalDate> targetDates(Instant cutoff, ZoneId zone) {
        var localCutoff = cutoff.atZone(zone).toLocalDate();
        var monday = localCutoff.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));
        return List.of(monday, monday.plusDays(1), monday.plusDays(2), monday.plusDays(3), monday.plusDays(4));
    }
    
}
