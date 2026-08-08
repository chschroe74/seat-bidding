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
@Table(name = "bidding_round")
public class BiddingRoundEntity extends AuditedEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public RoundStatus status;
    
    @Column(name = "sequence_no", nullable = false, unique = true)
    public long sequenceNo;
    
    @Column(name = "bidding_opens_at", nullable = false)
    public Instant biddingOpensAt;
    
    @Column(name = "cutoff_at", nullable = false, unique = true)
    public Instant cutoffAt;
    
    @Column(name = "schedule_zone", nullable = false, length = 64)
    public String scheduleZone;
    
    @Column(name = "tokens_granted", nullable = false)
    public int tokensGranted;
    
    @Column(name = "carry_over_cap", nullable = false)
    public int carryOverCap;
    
    @Column(name = "seat_capacity", nullable = false)
    public int seatCapacity;
    
    @Column(name = "processing_started_at")
    public Instant processingStartedAt;
    
    @Column(name = "processed_at")
    public Instant processedAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "predecessor_round_id", unique = true)
    public BiddingRoundEntity predecessor;
    
}

