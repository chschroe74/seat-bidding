package de.gigaworks.seatbidding.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "round_allocation_audit")
public class RoundAllocationAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false, unique = true)
    public BiddingRoundEntity round;

    @Column(name = "algorithm_version", nullable = false, length = 32, updatable = false)
    public String algorithmVersion;

    @Column(name = "input_fingerprint", nullable = false, length = 64, updatable = false)
    public String inputFingerprint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "objective_summary", nullable = false, columnDefinition = "jsonb", updatable = false)
    public String objectiveSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pairing_audit", nullable = false, columnDefinition = "jsonb", updatable = false)
    public String pairingAudit;

    @Column(name = "selected_solution_fingerprint", nullable = false, length = 64, updatable = false)
    public String selectedSolutionFingerprint;

    @Column(name = "random_selection_value", length = 255, updatable = false)
    public String randomSelectionValue;

    @Column(name = "capacity_selection_value", length = 255, updatable = false)
    public String capacitySelectionValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @jakarta.persistence.PrePersist
    void createTimestamp() {
        createdAt = Instant.now();
    }

}