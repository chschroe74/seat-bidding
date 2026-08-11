package de.gigaworks.seatbidding.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "seat_reservation")
public class SeatReservationEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    
    @Column(name = "target_date", nullable = false, unique = true)
    public LocalDate targetDate;
    
    @Column(name = "reserved_seat_count", nullable = false)
    public int reservedSeatCount;
    
    @Column(length = 500)
    public String description;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_employee_id", nullable = false)
    public EmployeeEntity createdBy;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
    
    @PrePersist
    void createTimestamp() {
        createdAt = Instant.now();
    }
    
}
