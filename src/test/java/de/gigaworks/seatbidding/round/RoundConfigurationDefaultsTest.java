package de.gigaworks.seatbidding.round;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RoundConfigurationDefaultsTest {

    @Test
    void liveRoundDefaultsAreSixtyTokensAndTwentyFourCarryOver() throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream("application.yaml")) {
            var yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yaml.contains("tokens-per-round: ${TOKENS_PER_ROUND:60}"));
            assertTrue(yaml.contains("carry-over-cap: ${CARRY_OVER_CAP:24}"));
        }
    }

}