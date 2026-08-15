package de.gigaworks.seatbidding.persistence;

import de.gigaworks.seatbidding.allocation.AllocationResolution;
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

@Entity
@Table(name = "allocation_unit")
public class AllocationUnitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_date_id", nullable = false)
    public RoundDateEntity roundDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false, length = 20, updatable = false)
    public AllocationUnitType unitType;

    @Column(name = "score_tokens", nullable = false, updatable = false)
    public int scoreTokens;

    @Column(name = "fairness_identity", nullable = false, length = 128, updatable = false)
    public String fairnessIdentity;

    @Column(nullable = false, updatable = false)
    public boolean assigned;

    @Column(name = "score_rank", nullable = false, updatable = false)
    public int scoreRank;

    @Column(name = "final_rank", nullable = false, updatable = false)
    public int finalRank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    public AllocationResolution resolution;

    @Column(name = "boundary_tie_group", length = 64, updatable = false)
    public String boundaryTieGroup;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    void createTimestamp() {
        createdAt = Instant.now();
    }

}