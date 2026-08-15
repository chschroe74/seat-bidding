package de.gigaworks.seatbidding.notification;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@ApplicationScoped
@Alternative
@Priority(1)
public class FakeWebPushTransport implements WebPushTransport {

    private final ConcurrentLinkedQueue<SendResult> results = new ConcurrentLinkedQueue<>();
    private final List<Message> messages = java.util.Collections.synchronizedList(new ArrayList<>());

    @Override
    public SendResult send(Message message) {
        messages.add(message);
        SendResult result = results.poll();
        return result == null ? SendResult.accepted(201) : result;
    }

    void reset(SendResult... values) {
        results.clear();
        messages.clear();
        results.addAll(List.of(values));
    }

    List<Message> messages() {
        return List.copyOf(messages);
    }

}