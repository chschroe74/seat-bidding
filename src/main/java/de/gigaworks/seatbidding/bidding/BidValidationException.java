package de.gigaworks.seatbidding.bidding;

import de.gigaworks.seatbidding.exception.SeatBiddingException;

public class BidValidationException extends SeatBiddingException {

    private final String code;

    public BidValidationException(String code, String message, Object... arguments) {
        super(message, arguments);
        this.code = code;
    }

    public String code() {
        return code;
    }

}
