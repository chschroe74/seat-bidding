package de.gigaworks.seatbidding.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class SeatBiddingExceptionTest {

    @Test
    void formatsMessageArguments() {
        var exception = new ConfigurationException("Property {} has invalid value {}", "scheduler.cron", 42);

        assertEquals("Property scheduler.cron has invalid value 42", exception.getMessage());
    }

    @Test
    void safelyFormatsNullDollarSignsAndBackslashes() {
        var exception = new ConfigurationException("Values: {}, {}, {}", null, "$1", "C:\\secrets");

        assertEquals("Values: null, $1, C:\\secrets", exception.getMessage());
    }

    @Test
    void substitutesNullForMissingArgumentsAndIgnoresAdditionalArguments() {
        assertEquals("Values: first, null", new ConfigurationException("Values: {}, {}", "first").getMessage());
        assertEquals("Value: first", new ConfigurationException("Value: {}", "first", "unused").getMessage());
    }

    @Test
    void retainsCause() {
        var cause = new IllegalArgumentException("invalid");
        var exception = new ConfigurationException(cause, "Invalid property {}", "scheduler.cron");

        assertEquals("Invalid property scheduler.cron", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

}
