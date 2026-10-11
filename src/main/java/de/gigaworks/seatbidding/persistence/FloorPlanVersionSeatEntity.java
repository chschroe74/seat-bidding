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
import java.math.BigDecimal;

@Entity
@Table(name = "floor_plan_version_seat")
public class FloorPlanVersionSeatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "floor_plan_version_id", nullable = false)
    public FloorPlanVersionEntity version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "physical_seat_id", nullable = false)
    public PhysicalSeatEntity physicalSeat;

    @Column(name = "seat_number", nullable = false)
    public int seatNumber;

    @Column(name = "normalized_x", nullable = false)
    public BigDecimal normalizedX;

    @Column(name = "normalized_y", nullable = false)
    public BigDecimal normalizedY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public FloorPlanSeatState state;
}