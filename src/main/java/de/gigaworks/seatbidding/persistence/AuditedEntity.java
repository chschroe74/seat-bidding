package de.gigaworks.seatbidding.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;

@MappedSuperclass
public abstract class AuditedEntity {
    
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
    
    @PrePersist
    void createTimestamps() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }
    
    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }
    
}

