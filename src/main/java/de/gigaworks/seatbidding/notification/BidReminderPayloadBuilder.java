package de.gigaworks.seatbidding.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class BidReminderPayloadBuilder {

    public static final int PAYLOAD_VERSION = 1;
    public static final String TEMPLATE = "BID_REMINDER_V1";
    public static final String TITLE = "Seat bidding reminder";
    public static final String BODY = "You have not placed your bids for next week yet.";
    public static final String PLACE_BIDS_ACTION = "PLACE_BIDS";
    public static final String SKIP_REMINDERS_ACTION = "SKIP_REMINDERS";

    @Inject
    ObjectMapper objectMapper;

    public String build(long roundId) {
        String biddingRoute = "/bids?reminderRoundId=" + roundId;
        String suppressionRoute = "/settings/reminders/skip?roundId=" + roundId;
        var payload = new Payload(PAYLOAD_VERSION, TEMPLATE, TITLE, BODY, roundId, biddingRoute,
                suppressionRoute, List.of(PLACE_BIDS_ACTION, SKIP_REMINDERS_ACTION));
        try {
            return objectMapper.writeValueAsString(payload);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("The fixed bid-reminder payload could not be encoded", exception);
        }
    }

    public record Payload(int version, String template, String title, String body, long roundId, String route,
            String suppressionRoute, List<String> actions) {
    }

}