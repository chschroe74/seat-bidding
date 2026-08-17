package de.gigaworks.seatbidding.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interaso.webpush.VapidKeys;
import com.interaso.webpush.WebPush;
import de.gigaworks.seatbidding.exception.ApplicationProblem;
import de.gigaworks.seatbidding.exception.ConfigurationException;
import io.quarkus.scheduler.Scheduled;
import java.net.InetAddress;
import java.net.URI;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReminderPrimitivesTest {

    private static final String P256DH = base64(key(65, (byte) 4));
    private static final String AUTH = base64(key(16, (byte) 1));
    private static final String VAPID_PUBLIC_KEY =
            "BJ7tFVi60Tw6Y0xchiuEZjd62SBdsK0_-zDEk3W-blZy0hB5UUUkbiq6BZxeN5529gkofV463kj-WAaBib5vxYY";
    private static final String VAPID_PRIVATE_KEY = "I4dqtEfeVxGFPO5i0TJugmklqM_g8uzIa-xwrgMMLok";

    @Test
    void weekdaysBecomeEligibleWithoutIncludingTheWeekend() {
        assertEquals(List.of(ReminderStartWeekday.MONDAY),
                ReminderStartWeekday.eligibleOn(DayOfWeek.MONDAY));
        assertEquals(List.of(ReminderStartWeekday.MONDAY, ReminderStartWeekday.TUESDAY,
                ReminderStartWeekday.WEDNESDAY, ReminderStartWeekday.THURSDAY,
                ReminderStartWeekday.FRIDAY), ReminderStartWeekday.eligibleOn(DayOfWeek.FRIDAY));
        assertTrue(ReminderStartWeekday.TUESDAY.hasStarted(DayOfWeek.THURSDAY));
        assertFalse(ReminderStartWeekday.FRIDAY.hasStarted(DayOfWeek.THURSDAY));
        assertTrue(ReminderStartWeekday.eligibleOn(DayOfWeek.SATURDAY).isEmpty());
    }

    @Test
    void reminderCronMustBeOneFixedWeekdayTime() {
        var schedule = new ReminderSchedule();
        assertEquals(LocalTime.of(10, 0), schedule.localTime("0 0 10 ? * MON-FRI"));
        assertThrows(ConfigurationException.class, () -> schedule.localTime("0 */5 * ? * *"));
        assertThrows(ConfigurationException.class, () -> schedule.localTime("0 0 25 ? * MON-FRI"));
    }

    @Test
    void payloadContainsOnlyRecognizedSafeRoutesAndFixedContent() throws Exception {
        var builder = new BidReminderPayloadBuilder();
        builder.objectMapper = new ObjectMapper();
        var payload = new ObjectMapper().readTree(builder.build(42));
        assertEquals(1, payload.get("version").intValue());
        assertEquals("Seat bidding reminder", payload.get("title").textValue());
        assertEquals("You have not placed your bids for next week yet.", payload.get("body").textValue());
        assertEquals("/bids?reminderRoundId=42", payload.get("route").textValue());
        assertEquals("/settings/reminders/skip?roundId=42", payload.get("suppressionRoute").textValue());
        assertFalse(payload.toString().contains("email"));
        assertFalse(payload.toString().contains("tokens"));
    }

    @Test
    void pushSubscriptionValidationRejectsSecretAndSsrfHazards() throws Exception {
        var validator = validator(List.of(InetAddress.getByName("203.0.113.10")));
        var validated = validator.validate("https://push.example.test/send?id=1", P256DH, AUTH,
                Instant.parse("2026-08-16T00:00:00Z"), "  My browser  ",
                Instant.parse("2026-08-15T00:00:00Z"));
        assertEquals("My browser", validated.deviceLabel());
        assertThrows(ApplicationProblem.class, () -> validator.validate("http://push.example.test", P256DH,
                AUTH, null, "Browser", Instant.EPOCH));
        assertThrows(ApplicationProblem.class, () -> validator.validate("https://user:secret@push.example.test",
                P256DH, AUTH, null, "Browser", Instant.EPOCH));
        assertThrows(ApplicationProblem.class, () -> validator.validate("https://push.example.test/#secret",
                P256DH, AUTH, null, "Browser", Instant.EPOCH));
        assertThrows(ApplicationProblem.class, () -> validator.validate("https://push.example.test", "bad",
                AUTH, null, "Browser", Instant.EPOCH));
        assertThrows(ApplicationProblem.class, () -> validator.validate("https://push.example.test", P256DH,
                AUTH, Instant.EPOCH, "Browser", Instant.EPOCH));

        var internal = validator(List.of(InetAddress.getByName("127.0.0.1")));
        assertThrows(ApplicationProblem.class, () -> internal.validate("https://push.example.test", P256DH,
                AUTH, null, "Browser", Instant.EPOCH));
    }

    @Test
    void schedulerHasOneStableNonConcurrentConfiguredTrigger() throws Exception {
        var scheduled = ReminderScheduler.class.getDeclaredMethod("run").getAnnotation(Scheduled.class);
        assertEquals("seat-bidding-bid-reminders", scheduled.identity());
        assertEquals("${seat-bidding.reminders.schedule.cron}", scheduled.cron());
        assertEquals("${seat-bidding.time-zone}", scheduled.timeZone());
        assertEquals(Scheduled.ConcurrentExecution.SKIP, scheduled.concurrentExecution());
        assertEquals(ReminderScheduler.SchedulerDisabled.class, scheduled.skipExecutionIf());
    }

    @Test
    void providerResponsesHaveConservativeAcceptanceAndInvalidationSemantics() {
        assertEquals(PushDeliveryOutcome.ACCEPTED, StandardsWebPushTransport.classify(201).outcome());
        assertEquals(PushDeliveryOutcome.PERMANENT_FAILURE, StandardsWebPushTransport.classify(404).outcome());
        assertEquals(PushDeliveryOutcome.PERMANENT_FAILURE, StandardsWebPushTransport.classify(410).outcome());
        assertEquals(PushDeliveryOutcome.TEMPORARY_FAILURE, StandardsWebPushTransport.classify(403).outcome());
        assertEquals(PushDeliveryOutcome.TEMPORARY_FAILURE, StandardsWebPushTransport.classify(429).outcome());
        assertEquals(PushDeliveryOutcome.TEMPORARY_FAILURE, StandardsWebPushTransport.classify(500).outcome());
    }

    @Test
    void transportBuildsAProviderSpecificSignedVapidAuthorizationHeader() {
        var keys = VapidKeys.fromUncompressedBytes(VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY);
        var webPush = new WebPush("mailto:test@test.invalid", keys);
        var endpoint = URI.create("https://updates.push.services.mozilla.com/wpush/v2/subscription");

        var headers = StandardsWebPushTransport.headers(webPush, endpoint, 43200, "reminder-topic");
        String authorization = headers.get("Authorization");

        assertTrue(authorization.startsWith("vapid t=ey"));
        assertTrue(authorization.contains("k=" + VAPID_PUBLIC_KEY));
        assertFalse(authorization.contains(endpoint.toASCIIString()));
        assertEquals("aes128gcm", headers.get("Content-Encoding"));
        assertEquals("43200", headers.get("TTL"));
        assertEquals("reminder-topic", headers.get("Topic"));
    }

    private static PushEndpointValidator validator(List<InetAddress> addresses) {
        var validator = new PushEndpointValidator();
        validator.addressResolver = host -> addresses;
        return validator;
    }

    private static byte[] key(int length, byte first) {
        byte[] result = new byte[length];
        result[0] = first;
        return result;
    }

    private static String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

}