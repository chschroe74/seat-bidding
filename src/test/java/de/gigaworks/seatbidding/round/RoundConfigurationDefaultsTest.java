package de.gigaworks.seatbidding.round;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RoundConfigurationDefaultsTest {

    @Test
    void liveRoundDefaultsAreSixtyTokensAndTwentyFourCarryOver() throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream("application.yaml")) {
            var yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertTrue(yaml.contains("tokens-per-round: ${TOKENS_PER_ROUND:60}"));
            assertTrue(yaml.contains("carry-over-cap: ${CARRY_OVER_CAP:24}"));
            assertTrue(yaml.contains("time-zone: \"${SEAT_BIDDING_TIME_ZONE:Europe/Berlin}\""));
            assertTrue(yaml.contains("cron: \"${SEAT_ASSIGNMENT_CRON:0 0 22 ? * FRI}\""));
            assertTrue(yaml.contains("cron: \"${BID_REMINDER_CRON:0 0 10 ? * MON-FRI}\""));
            assertTrue(yaml.contains("static-resources:\n      max-age: 0"));
        }
    }

}