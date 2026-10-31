package de.gigaworks.seatbidding.persistence;

public enum OfferCandidateStatus {
    PENDING,
    CLAIMED,
    DECLINED,
    AUTO_DECLINED_INSUFFICIENT_FUNDS,
    CLAIMED_BY_OTHER,
    EXPIRED
}