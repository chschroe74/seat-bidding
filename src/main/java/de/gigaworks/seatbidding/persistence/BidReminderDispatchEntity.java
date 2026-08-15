package de.gigaworks.seatbidding.persistence;

import de.gigaworks.seatbidding.notification.ReminderDispatchStatus;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bid_reminder_dispatch")
public class BidReminderDispatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    public BiddingRoundEntity round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    public EmployeeEntity employee;

    @Column(name = "business_date", nullable = false)
    public LocalDate businessDate;

    @Column(name = "scheduled_for", nullable = false)
    public Instant scheduledFor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public ReminderDispatchStatus status;

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

    @PrePersist
    void createTimestamp() {
        createdAt = Instant.now();
    }

}