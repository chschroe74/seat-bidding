package de.gigaworks.seatbidding.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "post_allocation_booking")
public class PostAllocationBookingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_occupancy_id", nullable = false, unique = true)
    public SeatOccupancyEntity occupancy;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_type", nullable = false, length = 24)
    public PostAllocationBookingType bookingType;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_candidate_id", unique = true)
    public SeatOfferCandidateEntity offerCandidate;

    @Column(name = "required_tokens", nullable = false)
    public int requiredTokens;

    @Column(name = "credit_tokens_spent", nullable = false)
    public int creditTokensSpent;

    @Column(name = "ordinary_tokens_spent", nullable = false)
    public int ordinaryTokensSpent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordinary_participation_id")
    public RoundParticipationEntity ordinaryParticipation;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @jakarta.persistence.PrePersist
    void createTimestamp() {
        createdAt = Instant.now();
    }
}