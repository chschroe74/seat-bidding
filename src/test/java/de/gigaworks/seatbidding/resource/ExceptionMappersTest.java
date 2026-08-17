package de.gigaworks.seatbidding.resource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExceptionMappersTest {

    @Test
    void onlyKnownOptionalBrowserProbesAreQuiet() {
        for (String path : new String[] {
                "/flutter.js.map",
                "/favicon.ico",
                "/apple-touch-icon.png",
                "/apple-touch-icon-precomposed.png",
                "/apple-touch-icon-120x120.png",
                "/apple-touch-icon-120x120-precomposed.png"}) {
            assertTrue(ExceptionMappers.isOptionalBrowserProbe(path));
            assertTrue(ExceptionMappers.isOptionalBrowserProbe(path.substring(1)));
        }

        assertFalse(ExceptionMappers.isOptionalBrowserProbe("/api/unknown"));
        assertFalse(ExceptionMappers.isOptionalBrowserProbe("/main.dart.js"));
        assertFalse(ExceptionMappers.isOptionalBrowserProbe("/unknown.png"));
    }
}