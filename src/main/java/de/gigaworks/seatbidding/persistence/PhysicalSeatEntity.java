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

@Entity
@Table(name = "physical_seat")
public class PhysicalSeatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "floor_plan_id", nullable = false)
    public FloorPlanEntity floorPlan;

    @Column(name = "seat_number", nullable = false)
    public int seatNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "first_published_version_id")
    public FloorPlanVersionEntity firstPublishedVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retired_in_version_id")
    public FloorPlanVersionEntity retiredInVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @jakarta.persistence.PrePersist
    void createTimestamp() {
        createdAt = Instant.now();
    }
}