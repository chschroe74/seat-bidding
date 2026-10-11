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

@Entity
@Table(name = "floor_plan")
public class FloorPlanEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "permanent_number_watermark", nullable = false)
    public int permanentNumberWatermark;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_version_id")
    public FloorPlanVersionEntity activeVersion;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_version_id")
    public FloorPlanVersionEntity pendingVersion;

    @Column(name = "pending_deactivation", nullable = false)
    public boolean pendingDeactivation;

    @Column(name = "state_revision", nullable = false)
    public long stateRevision;
}