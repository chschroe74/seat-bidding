package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.util.Optional;

@ApplicationScoped
public class EmployeeRepository implements PanacheRepositoryBase<EmployeeEntity, Long> {
    
    public Optional<EmployeeEntity> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }
    
    public Optional<EmployeeEntity> findByEmailForUpdate(String email) {
        return find("email", email).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }
    
    public Optional<EmployeeEntity> findByIdForUpdate(Long id) {
        return find("id", id).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }
    
}
