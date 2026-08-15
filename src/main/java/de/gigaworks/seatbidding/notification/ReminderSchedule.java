package de.gigaworks.seatbidding.notification;

import de.gigaworks.seatbidding.exception.ConfigurationException;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalTime;
import java.util.regex.Pattern;

@ApplicationScoped
public class ReminderSchedule {

    private static final Pattern WEEKDAY_CRON = Pattern.compile(
            "\\s*(\\d{1,2})\\s+(\\d{1,2})\\s+(\\d{1,2})\\s+\\?\\s+\\*\\s+MON-FRI\\s*",
            Pattern.CASE_INSENSITIVE);

    public LocalTime localTime(String expression) {
        var match = WEEKDAY_CRON.matcher(expression);
        if (!match.matches()) {
            throw new ConfigurationException(
                    "Reminder cron {} must be one fixed local-time Monday-through-Friday trigger", expression);
        }
        try {
            return LocalTime.of(Integer.parseInt(match.group(3)), Integer.parseInt(match.group(2)),
                    Integer.parseInt(match.group(1)));
        }
        catch (RuntimeException exception) {
            throw new ConfigurationException(exception, "Invalid reminder scheduler cron expression {}", expression);
        }
    }

}