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
@Table(name = "guest_seat_preference_revision")
public class GuestSeatPreferenceRevisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "floor_plan_id", nullable = false)
    public FloorPlanEntity floorPlan;

    @Column(name = "revision_no", nullable = false)
    public long revisionNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_employee_id", nullable = false)
    public EmployeeEntity createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}