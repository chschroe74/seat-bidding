package de.gigaworks.seatbidding.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "relinquishment_credit")
public class RelinquishmentCreditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_assignment_id", nullable = false, unique = true)
    public SeatAssignmentEntity seatAssignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    public EmployeeEntity employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    public BiddingRoundEntity round;

    @Column(name = "initial_tokens", nullable = false)
    public int initialTokens;

    @Column(name = "remaining_tokens", nullable = false)
    public int remainingTokens;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "fully_consumed_at")
    public Instant fullyConsumedAt;

    @Column(name = "expired_at")
    public Instant expiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @jakarta.persistence.PrePersist
    void createTimestamp() {
        createdAt = Instant.now();
    }
}