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
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "token_ledger")
public class TokenLedgerEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    public EmployeeEntity employee;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    public BiddingRoundEntity round;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bid_id")
    public BidEntity bid;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public LedgerType type;
    
    @Column(nullable = false)
    public int amount;
    
    @Column(name = "idempotency_key", nullable = false, unique = true)
    public String idempotencyKey;
    
    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
    
    @jakarta.persistence.PrePersist
    void createTimestamp() {
        createdAt = Instant.now();
    }
    
}

