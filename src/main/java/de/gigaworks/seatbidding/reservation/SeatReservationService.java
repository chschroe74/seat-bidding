package de.gigaworks.seatbidding.reservation;

import de.gigaworks.seatbidding.auth.EmployeeIdentityService;
import de.gigaworks.seatbidding.dto.CreateSeatReservationRequest;
import de.gigaworks.seatbidding.dto.SeatReservationListResponse;
import de.gigaworks.seatbidding.dto.SeatReservationResponse;
import de.gigaworks.seatbidding.exception.ApplicationProblem;
import de.gigaworks.seatbidding.persistence.BiddingRoundEntity;
import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.persistence.RoundStatus;
import de.gigaworks.seatbidding.persistence.SeatReservationEntity;
import de.gigaworks.seatbidding.persistence.SeatReservationRepository;
import de.gigaworks.seatbidding.round.SeatBiddingConfiguration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import java.net.URI;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class SeatReservationService {
    
    @Inject
    EmployeeIdentityService identity;
    
    @Inject
    SeatReservationRepository reservations;
    
    @Inject
    BiddingRoundRepository rounds;
    
    @Inject
    SeatBiddingConfiguration configuration;
    
    @Inject
    Clock clock;
    
    @Transactional
    public SeatReservationListResponse list(String fromValue, String toValue) {
        identity.requireCurrentAdmin();
        var from = parseDate(fromValue, "from");
        var to = parseDate(toValue, "to");
        if (from.isAfter(to)) {
            throw invalidRange("The start date must not be after the end date.");
        }
        if (ChronoUnit.DAYS.between(from, to) > 365) {
            throw invalidRange("The inclusive date range must not exceed 366 days.");
        }
        var now = clock.instant();
        var values = reservations.findBetween(from, to).stream()
                .map(reservation -> response(reservation, rounds.findForTargetDate(reservation.targetDate).orElse(null), now))
                .toList();
        return new SeatReservationListResponse(now, configuration.scheduler().timeZone().getId(), values);
    }
    
    @Transactional
    public CreatedReservation create(CreateSeatReservationRequest request) {
        var date = request.date();
        validateDate(date);
        var round = rounds.findForTargetDateForUpdate(date).orElse(null);
        var actor = identity.requireCurrentAdmin();
        var now = clock.instant();
        int capacity = applicableCapacity(round, now);
        if (request.reservedSeatCount() > capacity) {
            throw ApplicationProblem.badRequest("RESERVATION_CAPACITY_EXCEEDED", "Reservation exceeds capacity",
                    "The reserved seat count must not exceed " + capacity + ".");
        }
        String description = normalizeDescription(request.description());
        if (reservations.findByTargetDate(date).isPresent()) {
            throw duplicate(date);
        }
        var reservation = new SeatReservationEntity();
        reservation.targetDate = date;
        reservation.reservedSeatCount = request.reservedSeatCount();
        reservation.description = description;
        reservation.createdBy = actor;
        try {
            reservations.persistAndFlush(reservation);
        }
        catch (PersistenceException exception) {
            if (isDuplicate(exception)) {
                throw duplicate(date);
            }
            throw exception;
        }
        log.info("operation=reservation-create outcome=success reservationId={} targetDate={} count={} employeeId={}",
                reservation.id, reservation.targetDate, reservation.reservedSeatCount, actor.id);
        return new CreatedReservation(URI.create("/api/admin/seat-reservations/" + reservation.id),
                response(reservation, round, now));
    }
    
    @Transactional
    public void delete(long reservationId) {
        var existing = reservations.findByIdOptional(reservationId).orElseThrow(() ->
                ApplicationProblem.notFound("RESERVATION_NOT_FOUND", "The reservation does not exist."));
        var round = rounds.findForTargetDateForUpdate(existing.targetDate).orElse(null);
        var actor = identity.requireCurrentAdmin();
        var reservation = reservations.findByIdForUpdate(reservationId).orElseThrow(() ->
                ApplicationProblem.notFound("RESERVATION_NOT_FOUND", "The reservation does not exist."));
        ensureMutable(round, clock.instant());
        reservations.delete(reservation);
        log.info("operation=reservation-delete outcome=success reservationId={} targetDate={} count={} employeeId={}",
                reservation.id, reservation.targetDate, reservation.reservedSeatCount, actor.id);
    }
    
    private void validateDate(LocalDate date) {
        if (date == null) {
            throw ApplicationProblem.badRequest("RESERVATION_DATE_INVALID", "Invalid reservation date",
                    "A reservation date is required.");
        }
        var today = LocalDate.now(clock.withZone(configuration.scheduler().timeZone()));
        if (date.isBefore(today) || date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw ApplicationProblem.badRequest("RESERVATION_DATE_INVALID", "Invalid reservation date",
                    "The reservation date must be today or later and Monday through Friday.");
        }
    }
    
    private int applicableCapacity(BiddingRoundEntity round, java.time.Instant now) {
        if (round == null) {
            return configuration.seatCapacity();
        }
        ensureMutable(round, now);
        return round.seatCapacity;
    }
    
    private static void ensureMutable(BiddingRoundEntity round, java.time.Instant now) {
        if (round != null && (round.status != RoundStatus.OPEN || !now.isBefore(round.cutoffAt))) {
            throw ApplicationProblem.conflict("RESERVATION_IMMUTABLE", "Reservation is immutable",
                    "The reservation can no longer be changed because its round has closed.");
        }
    }
    
    private SeatReservationResponse response(SeatReservationEntity reservation, BiddingRoundEntity round,
            java.time.Instant now) {
        int capacity = round == null ? configuration.seatCapacity() : round.seatCapacity;
        boolean mutable = round == null || round.status == RoundStatus.OPEN && now.isBefore(round.cutoffAt);
        return new SeatReservationResponse(reservation.id, reservation.targetDate, reservation.reservedSeatCount,
                capacity, reservation.description, mutable, round == null ? null : round.cutoffAt,
                round == null ? null : round.status.name());
    }
    
    private static LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) {
            throw ApplicationProblem.badRequest("RESERVATION_RANGE_INVALID", "Invalid reservation range",
                    "Both from and to dates are required.");
        }
        try {
            return LocalDate.parse(value);
        }
        catch (DateTimeParseException exception) {
            throw ApplicationProblem.badRequest("RESERVATION_RANGE_INVALID", "Invalid reservation range",
                    field + " must use YYYY-MM-DD format.");
        }
    }
    
    private static ApplicationProblem invalidRange(String detail) {
        return ApplicationProblem.badRequest("RESERVATION_RANGE_INVALID", "Invalid reservation range", detail);
    }
    
    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.codePointCount(0, normalized.length()) > 500) {
            throw ApplicationProblem.badRequest("RESERVATION_DESCRIPTION_INVALID", "Invalid description",
                    "The reservation description must not exceed 500 Unicode characters.");
        }
        return normalized;
    }
    
    private static ApplicationProblem duplicate(LocalDate date) {
        return ApplicationProblem.conflict("RESERVATION_ALREADY_EXISTS", "Reservation already exists",
                "A reservation already exists for " + date + ".");
    }
    
    private static boolean isDuplicate(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (String.valueOf(current.getMessage()).contains("uq_reservation_target_date")) {
                return true;
            }
        }
        return false;
    }
    
    public record CreatedReservation(
            URI location,
            SeatReservationResponse response) {
        
    }
    
}
