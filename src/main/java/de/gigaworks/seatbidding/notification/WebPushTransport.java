package de.gigaworks.seatbidding.notification;

public interface WebPushTransport {

    SendResult send(Message message);

    record Message(long roundId, String endpoint, String p256dh, String auth, String payload, String topic) {
    }

    record SendResult(PushDeliveryOutcome outcome, Integer providerStatus) {

        public static SendResult accepted(int status) {
            return new SendResult(PushDeliveryOutcome.ACCEPTED, status);
        }

        public static SendResult temporary(Integer status) {
            return new SendResult(PushDeliveryOutcome.TEMPORARY_FAILURE, status);
        }

        public static SendResult permanent(int status) {
            return new SendResult(PushDeliveryOutcome.PERMANENT_FAILURE, status);
        }

    }

}