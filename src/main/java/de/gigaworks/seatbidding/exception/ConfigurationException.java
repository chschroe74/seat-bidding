package de.gigaworks.seatbidding.exception;

public class ConfigurationException extends SeatBiddingException {

    public ConfigurationException(String message, Object... arguments) {
        super(message, arguments);
    }

    public ConfigurationException(Throwable cause, String message, Object... arguments) {
        super(cause, message, arguments);
    }

}
