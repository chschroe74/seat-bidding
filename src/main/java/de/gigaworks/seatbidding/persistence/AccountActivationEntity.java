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
@Table(name = "account_activation")
public class AccountActivationEntity extends AuditedEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    public EmployeeEntity employee;
    
    @Column(name = "code_digest")
    public String codeDigest;
    
    @Column(name = "code_expires_at")
    public Instant codeExpiresAt;
    
    @Column(name = "failed_attempts", nullable = false)
    public int failedAttempts;
    
    @Column(name = "last_sent_at")
    public Instant lastSentAt;
    
    @Column(name = "activation_token_hash", length = 64, unique = true)
    public String activationTokenHash;
    
    @Column(name = "activation_token_expires_at")
    public Instant activationTokenExpiresAt;
    
    @Column(name = "verified_at")
    public Instant verifiedAt;
    
}
