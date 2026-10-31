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
@Table(name = "released_capacity_offer")
public class ReleasedCapacityOfferEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_relinquishment_id", nullable = false, unique = true)
    public AssignmentRelinquishmentEntity relinquishment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_date_id", nullable = false)
    public RoundDateEntity roundDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_period", nullable = false, length = 20)
    public AttendancePeriod attendancePeriod;

    @Column(name = "initial_morning_capacity", nullable = false)
    public int initialMorningCapacity;

    @Column(name = "initial_afternoon_capacity", nullable = false)
    public int initialAfternoonCapacity;

    @Column(nullable = false)
    public Instant deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public OfferStatus status;
}