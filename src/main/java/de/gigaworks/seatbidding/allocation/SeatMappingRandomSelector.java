package de.gigaworks.seatbidding.allocation;

public interface SeatMappingRandomSelector {

    Draw draw();

    record Draw(long seed, String auditValue) {
    }
}