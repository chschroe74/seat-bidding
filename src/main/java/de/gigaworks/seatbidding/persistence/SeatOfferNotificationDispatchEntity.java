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
@Table(name = "seat_offer_notification_dispatch")
public class SeatOfferNotificationDispatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, unique = true)
    public SeatOfferCandidateEntity candidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    public EmployeeEntity employee;

    @Column(nullable = false, length = 20)
    public String status;

    @Column(name = "subscription_count", nullable = false)
    public int subscriptionCount;

    @Column(name = "accepted_count", nullable = false)
    public int acceptedCount;

    @Column(name = "failed_count", nullable = false)
    public int failedCount;

    @Column(name = "completed_at")
    public Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @jakarta.persistence.PrePersist
    void createTimestamp() {
        createdAt = Instant.now();
    }
}