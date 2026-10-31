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
@Table(name = "seat_occupancy")
public class SeatOccupancyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_date_id", nullable = false)
    public RoundDateEntity roundDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    public EmployeeEntity employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_period", nullable = false, length = 20)
    public AttendancePeriod attendancePeriod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "physical_seat_id")
    public PhysicalSeatEntity physicalSeat;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 24)
    public OccupancySourceType sourceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_seat_assignment_id")
    public SeatAssignmentEntity originalSeatAssignment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public OccupancyStatus status;

    @Column(name = "occupied_at", nullable = false)
    public Instant occupiedAt;

    @Column(name = "relinquished_at")
    public Instant relinquishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @jakarta.persistence.PrePersist
    void createTimestamp() {
        createdAt = Instant.now();
    }
}