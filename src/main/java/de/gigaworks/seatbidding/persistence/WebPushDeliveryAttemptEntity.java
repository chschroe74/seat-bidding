package de.gigaworks.seatbidding.persistence;

import de.gigaworks.seatbidding.notification.PushDeliveryOutcome;

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
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "web_push_delivery_attempt")
public class WebPushDeliveryAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispatch_id", nullable = false)
    public BidReminderDispatchEntity dispatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "push_subscription_id", nullable = false)
    public WebPushSubscriptionEntity subscription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    public PushDeliveryOutcome outcome;

    @Column(name = "provider_status")
    public Integer providerStatus;

    @Column(name = "attempted_at", nullable = false)
    public Instant attemptedAt;

}