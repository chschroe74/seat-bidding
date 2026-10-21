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

@Entity
@Table(name = "guest_seat_preference")
public class GuestSeatPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    public GuestSeatPreferenceRevisionEntity revision;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "physical_seat_id", nullable = false)
    public PhysicalSeatEntity physicalSeat;

    @Column(nullable = false)
    public short stars;
}