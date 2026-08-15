package de.gigaworks.seatbidding.persistence;

import de.gigaworks.seatbidding.notification.PushSubscriptionStatus;

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
@Table(name = "web_push_subscription")
public class WebPushSubscriptionEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    public EmployeeEntity employee;

    @Column(name = "endpoint_hash", nullable = false, unique = true, length = 64)
    public String endpointHash;

    @Column(columnDefinition = "text")
    public String endpoint;

    @Column(name = "p256dh_key", columnDefinition = "text")
    public String p256dhKey;

    @Column(name = "auth_key", columnDefinition = "text")
    public String authKey;

    @Column(name = "expires_at")
    public Instant expiresAt;

    @Column(name = "device_label", nullable = false, length = 120)
    public String deviceLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public PushSubscriptionStatus status;

    @Column(name = "last_seen_at", nullable = false)
    public Instant lastSeenAt;

    @Column(name = "last_successful_push_at")
    public Instant lastSuccessfulPushAt;

    @Column(name = "invalidated_at")
    public Instant invalidatedAt;

    public void deactivate(PushSubscriptionStatus newStatus, Instant now) {
        status = newStatus;
        endpoint = null;
        p256dhKey = null;
        authKey = null;
        invalidatedAt = now;
    }

}