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
@Table(name = "seat_offer_candidate")
public class SeatOfferCandidateEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    public ReleasedCapacityOfferEntity offer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_date_id", nullable = false)
    public RoundDateEntity roundDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bid_id", nullable = false)
    public BidEntity bid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    public EmployeeEntity employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_period", nullable = false, length = 20)
    public AttendancePeriod attendancePeriod;

    @Column(name = "tier_tokens", nullable = false)
    public int tierTokens;

    @Column(name = "required_tokens", nullable = false)
    public int requiredTokens;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    public OfferCandidateStatus status;

    @Column(name = "offered_at", nullable = false)
    public Instant offeredAt;

    @Column(name = "responded_at")
    public Instant respondedAt;
}