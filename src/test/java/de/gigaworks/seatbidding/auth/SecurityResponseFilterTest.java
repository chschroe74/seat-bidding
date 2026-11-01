package de.gigaworks.seatbidding.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecurityResponseFilterTest {

    @Test
    void applicationPolicyAllowsReadingLocalFilePickerBlobs() {
        String policy = SecurityResponseFilter.contentSecurityPolicy("/");

        assertTrue(policy.contains("connect-src 'self' blob:;"));
    }

    @Test
    void servedFloorPlanSvgRetainsItsRestrictedPolicy() {
        assertEquals("default-src 'none'; style-src 'unsafe-inline'",
                SecurityResponseFilter.contentSecurityPolicy("/api/floor-plan/versions/42/svg"));
    }

}