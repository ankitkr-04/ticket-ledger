package com.ticketledger.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ticketledger.domain.model.entity.Seat;
import com.ticketledger.domain.model.enums.SeatStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface SeatRepository extends JpaRepository<Seat, UUID> {

    /**
     * Locks seats for update with a deterministic ordering to prevent deadlocks.
     * Uses PESSIMISTIC_WRITE (SELECT ... FOR UPDATE).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id IN :ids ORDER BY s.id")
    List<Seat> lockSeats(@Param("ids") List<UUID> ids);

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