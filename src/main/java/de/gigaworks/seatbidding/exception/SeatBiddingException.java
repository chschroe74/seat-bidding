package de.gigaworks.seatbidding.exception;

public abstract class SeatBiddingException extends RuntimeException {

    protected SeatBiddingException(String message, Object... arguments) {
        super(ExceptionHelper.prepareMessage(message, arguments));
    }

    protected SeatBiddingException(Throwable cause, String message, Object... arguments) {
        super(ExceptionHelper.prepareMessage(message, arguments), cause);
    }

}
