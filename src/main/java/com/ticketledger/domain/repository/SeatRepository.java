package com.ticketledger.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import com.ticketledger.domain.entity.Seat;
import com.ticketledger.domain.enums.SeatStatus;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

@Repository
public interface SeatRepository extends JpaRepository<Seat, UUID> {

    /**
     * Locks seats for update with a deterministic ordering to prevent deadlocks.
     * Uses PESSIMISTIC_WRITE (SELECT ... FOR UPDATE).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({ @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000") }) // Fail fast after 3s
    @Query("SELECT s FROM Seat s " +
            "LEFT JOIN FETCH s.tier " + // Fetch Tier in same query
            "LEFT JOIN FETCH s.showtime " + // Fetch Showtime in same query
            "WHERE s.id IN :seatIds")
    List<Seat> lockSeats(List<UUID> seatIds);

    /**
     * Magic method to find available seats for a specific showtime.
     */
    List<Seat> findByShowtimeIdAndStatus(UUID showtimeId, SeatStatus status);

    /**
     * Default method to encapsulate the 'AVAILABLE' status logic.
     */
    default List<Seat> findAvailableSeats(UUID showtimeId) {
        return findByShowtimeIdAndStatus(showtimeId, SeatStatus.AVAILABLE);
    }
}