package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.util.Optional;

@ApplicationScoped
public class AccountActivationRepository implements PanacheRepositoryBase<AccountActivationEntity, Long> {
    
    public Optional<AccountActivationEntity> findByEmployee(Long employeeId) {
        return find("employee.id", employeeId).firstResultOptional();
    }
    
    public Optional<AccountActivationEntity> findByEmployeeForUpdate(Long employeeId) {
        return find("employee.id", employeeId).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }
    
    public Optional<AccountActivationEntity> findByTokenHashForUpdate(String hash) {
        return find("activationTokenHash", hash).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }
    
    public Optional<AccountActivationEntity> findByTokenHash(String hash) {
        return find("activationTokenHash", hash).firstResultOptional();
    }
    
}
