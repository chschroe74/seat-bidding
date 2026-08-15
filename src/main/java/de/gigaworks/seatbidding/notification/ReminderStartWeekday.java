package de.gigaworks.seatbidding.notification;

import java.time.DayOfWeek;
import java.util.List;

public enum ReminderStartWeekday {

    MONDAY(DayOfWeek.MONDAY),
    TUESDAY(DayOfWeek.TUESDAY),
    WEDNESDAY(DayOfWeek.WEDNESDAY),
    THURSDAY(DayOfWeek.THURSDAY),
    FRIDAY(DayOfWeek.FRIDAY);

    private final DayOfWeek dayOfWeek;

    ReminderStartWeekday(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public boolean hasStarted(DayOfWeek current) {
        return current.getValue() >= dayOfWeek.getValue() && current.getValue() <= DayOfWeek.FRIDAY.getValue();
    }

    public static List<ReminderStartWeekday> eligibleOn(DayOfWeek current) {
        return java.util.Arrays.stream(values()).filter(value -> value.hasStarted(current)).toList();
    }

}