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
@Table(name = "round_participation")
public class RoundParticipationEntity extends AuditedEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    public BiddingRoundEntity round;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    public EmployeeEntity employee;
    
    @Column(name = "grant_tokens", nullable = false)
    public int grantTokens;
    
    @Column(name = "carried_in_tokens", nullable = false)
    public int carriedInTokens;
    
    @Column(name = "starting_balance", nullable = false)
    public int startingBalance;
    
    @Column(name = "successful_bid_tokens", nullable = false)
    public int successfulBidTokens;
    
    @Column(name = "remaining_balance")
    public Integer remainingBalance;
    
    @Column(name = "carried_out_tokens")
    public Integer carriedOutTokens;
    
}

