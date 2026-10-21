package de.gigaworks.seatbidding.floorplan;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.gigaworks.seatbidding.exception.ApplicationProblem;
import de.gigaworks.seatbidding.round.SeatBiddingConfiguration;
import java.lang.reflect.Proxy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SvgSanitizerTest {

    private final SvgSanitizer sanitizer = sanitizer();

    @Test
    void normalizesDrawIoSvgWhilePreservingLocalPatterns() {
        var result = sanitizer.sanitize("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"
                     style="background: white; color-scheme: light dark;">
                  <style type="text/css">@supports (color: light-dark(#000, #fff)) { svg { color: red; } }</style>
                  <defs>
                    <pattern id="hatch" patternUnits="userSpaceOnUse" width="10" height="10"
                             patternTransform="rotate(45)">
                      <line x1="0" y1="0" x2="10" y2="10" stroke="#aaa" stroke-width="1"/>
                    </pattern>
                  </defs>
                  <rect data-cell-id="desk" pointer-events="all" style="fill: red" x="0" y="0"
                        width="100" height="100" fill="url(#hatch)"/>
                </svg>
                """.getBytes(UTF_8));

        assertFalse(result.content().contains("DOCTYPE"));
        assertFalse(result.content().contains("<style"));
        assertFalse(result.content().contains("style="));
        assertFalse(result.content().contains("data-cell-id"));
        assertFalse(result.content().contains("pointer-events"));
        assertTrue(result.content().contains("<pattern"));
        assertTrue(result.content().contains("patternUnits=\"userSpaceOnUse\""));
        assertTrue(result.content().contains("patternTransform=\"rotate(45)\""));
        assertTrue(result.content().contains("fill=\"url(#hatch)\""));
        assertEquals("100", result.width().toPlainString());
        assertEquals("100", result.height().toPlainString());
    }

    @Test
    void rejectsCustomDoctypesAndEntities() {
        var problem = assertThrows(ApplicationProblem.class, () -> sanitizer.sanitize("""
                <!DOCTYPE svg [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><text>&secret;</text></svg>
                """.getBytes(UTF_8)));

        assertEquals(400, problem.status());
        assertEquals("INVALID_SVG", problem.code());
    }

    @Test
    void rejectsActiveAndExternalContent() {
        assertInvalid("<script/>");
        assertInvalid("<rect width=\"1\" height=\"1\" onclick=\"alert(1)\"/>");
        assertInvalid("<rect width=\"1\" height=\"1\" href=\"https://example.com/tracker\"/>");
        assertInvalid("<rect width=\"1\" height=\"1\" fill=\"url(https://example.com/fill.svg)\"/>");
    }

    private void assertInvalid(String content) {
        var problem = assertThrows(ApplicationProblem.class, () -> sanitizer.sanitize(("""
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
                """ + content + "</svg>").getBytes(UTF_8)));
        assertEquals("INVALID_SVG", problem.code());
    }

    private static SvgSanitizer sanitizer() {
        var floorPlan = (SeatBiddingConfiguration.FloorPlan) Proxy.newProxyInstance(
                SeatBiddingConfiguration.FloorPlan.class.getClassLoader(),
                new Class<?>[]{SeatBiddingConfiguration.FloorPlan.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "maxActiveSeats" -> 500;
                    case "maxUploadBytes" -> 5_242_880L;
                    case "maxElements" -> 50_000;
                    case "maxPathCommands" -> 250_000;
                    case "parseTimeout" -> Duration.ofSeconds(5);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var configuration = (SeatBiddingConfiguration) Proxy.newProxyInstance(
                SeatBiddingConfiguration.class.getClassLoader(),
                new Class<?>[]{SeatBiddingConfiguration.class},
                (proxy, method, arguments) -> {
                    if ("floorPlan".equals(method.getName())) {
                        return floorPlan;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        var result = new SvgSanitizer();
        result.configuration = configuration;
        return result;
    }

}