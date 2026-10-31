package de.gigaworks.seatbidding.occupancy;

import de.gigaworks.seatbidding.persistence.AttendancePeriod;

public record CapacityVector(int morningAvailable, int afternoonAvailable) {

    public CapacityVector {
        if (morningAvailable < 0 || afternoonAvailable < 0) {
            throw new IllegalArgumentException("Capacity availability cannot be negative");
        }
    }

    public boolean supports(AttendancePeriod period) {
        return switch (period) {
            case FULL_DAY -> morningAvailable > 0 && afternoonAvailable > 0;
            case MORNING_ONLY -> morningAvailable > 0;
            case AFTERNOON_ONLY -> afternoonAvailable > 0;
        };
    }

    public CapacityVector consume(AttendancePeriod period) {
        if (!supports(period)) {
            throw new IllegalArgumentException("Attendance period is unavailable");
        }
        return switch (period) {
            case FULL_DAY -> new CapacityVector(morningAvailable - 1, afternoonAvailable - 1);
            case MORNING_ONLY -> new CapacityVector(morningAvailable - 1, afternoonAvailable);
            case AFTERNOON_ONLY -> new CapacityVector(morningAvailable, afternoonAvailable - 1);
        };
    }
}