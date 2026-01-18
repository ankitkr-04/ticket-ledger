package com.ticketledger.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ticketledger.domain.model.entity.Booking;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /**
     * Reaper query: Finds bookings that are HELD but have passed their lock time.
     */
    @Query("SELECT b FROM Booking b WHERE b.status = 'HELD' AND b.lockedUntil < :now")
    List<Booking> findExpiredBookings(@Param("now") Instant now);

    /**
     * Magic method for user history, ordered by creation time descending.
     */
    List<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId);
}