package de.gigaworks.seatbidding.notification;

import com.interaso.webpush.VapidKeys;
import com.interaso.webpush.WebPush;
import de.gigaworks.seatbidding.round.SeatBiddingConfiguration;
import io.quarkus.arc.DefaultBean;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@ApplicationScoped
@DefaultBean
@Slf4j
public class StandardsWebPushTransport implements WebPushTransport {

    @Inject
    SeatBiddingConfiguration configuration;

    @Inject
    PushEndpointValidator endpointValidator;

    private WebPush webPush;
    private HttpClient client;
    private Duration requestTimeout;
    private int timeToLiveSeconds;

    @PostConstruct
    void initialize() {
        var push = configuration.reminders().webPush();
        var keys = VapidKeys.fromUncompressedBytes(push.vapidPublicKey(), push.vapidPrivateKey());
        webPush = new WebPush(push.vapidSubject(), keys);
        client = HttpClient.newBuilder().connectTimeout(push.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        requestTimeout = push.requestTimeout();
        timeToLiveSeconds = Math.toIntExact(push.timeToLive().toSeconds());
    }

    @Override
    public SendResult send(Message message) {
        try {
            var endpoint = endpointValidator.validateForDelivery(message.endpoint());
            byte[] body = webPush.getBody(message.payload().getBytes(StandardCharsets.UTF_8),
                    Base64.getUrlDecoder().decode(message.p256dh()),
                    Base64.getUrlDecoder().decode(message.auth()));
            var request = HttpRequest.newBuilder(endpoint).timeout(requestTimeout)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            webPush.getHeadersWithToken(endpoint.toASCIIString(), timeToLiveSeconds, message.topic(),
                    WebPush.Urgency.Normal).forEach(request::header);
            int status = client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
            return classify(status);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return SendResult.temporary(null);
        }
        catch (IOException | RuntimeException exception) {
            log.warn("operation=web-push-send outcome=temporary-failure providerStatus=none");
            return SendResult.temporary(null);
        }
    }

    static SendResult classify(int status) {
        if (status >= 200 && status < 300) {
            return SendResult.accepted(status);
        }
        if (status == 404 || status == 410) {
            return SendResult.permanent(status);
        }
        return SendResult.temporary(status);
    }

}