package de.gigaworks.seatbidding.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gigaworks.seatbidding.persistence.AttendancePeriod;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class SeatOfferPayloadBuilder {

    @Inject ObjectMapper objectMapper;

    public String build(long candidateId, long roundId, LocalDate date, AttendancePeriod period) {
        String localizedDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.ENGLISH).format(date);
        String route = "/assignments?roundId=" + roundId + "&date=" + date;
        var payload = new Payload(1, "SEAT_OFFER_V1", "Seat available",
                localizedDate + " · " + label(period)
                        + ". This time-sensitive opportunity may be first-come-first-served.",
                candidateId, roundId, route, List.of("VIEW_OFFER"));
        try {
            return objectMapper.writeValueAsString(payload);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("The fixed seat-offer payload could not be encoded", exception);
        }
    }

    private static String label(AttendancePeriod period) {
        return switch (period) {
            case FULL_DAY -> "Full day";
            case MORNING_ONLY -> "Morning";
            case AFTERNOON_ONLY -> "Afternoon";
        };
    }

    public record Payload(int version, String template, String title, String body, long candidateId, long roundId,
            String route, List<String> actions) {
    }
}