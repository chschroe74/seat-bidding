package de.gigaworks.seatbidding.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "round_date")
public class RoundDateEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    public BiddingRoundEntity round;
    
    @Column(name = "target_date", nullable = false)
    public LocalDate targetDate;
    
    @Column(nullable = false)
    public short ordinal;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
    
    @jakarta.persistence.PrePersist
    void createTimestamp() {
        createdAt = Instant.now();
    }
    
}

