package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class SeatReservationRepository implements PanacheRepositoryBase<SeatReservationEntity, Long> {
    
    public Optional<SeatReservationEntity> findByTargetDate(LocalDate date) {
        return find("targetDate", date).firstResultOptional();
    }
    
    public Optional<SeatReservationEntity> findByIdForUpdate(long id) {
        return find("id", id).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }
    
    public List<SeatReservationEntity> findBetween(LocalDate from, LocalDate to) {
        return list("targetDate between ?1 and ?2 order by targetDate", from, to);
    }
    
    public List<SeatReservationEntity> findForDatesForUpdate(Collection<LocalDate> dates) {
        if (dates.isEmpty()) {
            return List.of();
        }
        return find("targetDate in ?1 order by targetDate", dates)
                .withLock(LockModeType.PESSIMISTIC_WRITE).list();
    }
    
    public List<SeatReservationEntity> findForDates(Collection<LocalDate> dates) {
        if (dates.isEmpty()) {
            return List.of();
        }
        return find("targetDate in ?1 order by targetDate", dates).list();
    }
    
}
