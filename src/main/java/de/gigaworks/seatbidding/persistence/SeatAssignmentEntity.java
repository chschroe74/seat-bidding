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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "seat_assignment")
public class SeatAssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_date_id", nullable = false)
    public RoundDateEntity roundDate;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bid_id", nullable = false, unique = true)
    public BidEntity bid;

    @Column(nullable = false)
    public boolean assigned;

    @Column(name = "final_rank", nullable = false)
    public int finalRank;

    @Column(name = "token_rank", nullable = false, updatable = false)
    public int tokenRank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    public AllocationResolution resolution;

    @Column(name = "boundary_tie_group", length = 64, updatable = false)
    public String boundaryTieGroup;

    @Column(name = "tie_group", length = 64)
    public String tieGroup;

    @Column(name = "draw_value")
    public String drawValue;

    @Column(name = "algorithm_version", nullable = false, length = 32)
    public String algorithmVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @jakarta.persistence.PrePersist
    void createTimestamp() {
        createdAt = Instant.now();
    }

}
