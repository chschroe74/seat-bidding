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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "assignment_relinquishment")
public class AssignmentRelinquishmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_occupancy_id", nullable = false, unique = true)
    public SeatOccupancyEntity occupancy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    public EmployeeEntity employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_date_id", nullable = false)
    public RoundDateEntity roundDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_period", nullable = false, length = 20)
    public AttendancePeriod attendancePeriod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "released_physical_seat_id")
    public PhysicalSeatEntity releasedPhysicalSeat;

    @Column(name = "released_seat_number")
    public Integer releasedSeatNumber;

    @Column(name = "released_at", nullable = false)
    public Instant releasedAt;

    @Column(name = "offer_deadline", nullable = false)
    public Instant offerDeadline;

    @Column(name = "original_assignment", nullable = false)
    public boolean originalAssignment;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @jakarta.persistence.PrePersist
    void createTimestamp() {
        createdAt = Instant.now();
    }
}