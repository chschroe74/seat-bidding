package de.gigaworks.seatbidding.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "employee")
public class EmployeeEntity extends AuditedEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    
    @Column(nullable = false, unique = true, length = 320)
    public String email;
    
    @Column(name = "first_name", nullable = false)
    public String firstName;
    
    @Column(name = "last_name", nullable = false)
    public String lastName;
    
    @Column(name = "is_admin", nullable = false)
    public boolean admin;
    
    @Column(name = "password_hash", length = 512)
    public String passwordHash;
    
    @Column(name = "password_set_at")
    public java.time.Instant passwordSetAt;
    
    @Column(nullable = false)
    public boolean enabled = true;
    
}
