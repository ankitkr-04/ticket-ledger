package com.ticketledger.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ticketledger.domain.entity.Booking;
import com.ticketledger.domain.enums.BookingStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /**
     * Pessimistic write lock for concurrent payment webhook processing.
     * Prevents race condition with Reaper job expiring bookings.
     * Uses @Query to avoid Spring Data parsing "WithLock" as a property.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdWithLock(@Param("id") UUID id);

    /**
     * Find bookings by status that have expired (lockedUntil before now).
     * 
     * @param status
     * @param now
     * @param pageable
     * @return
     */
    List<Booking> findByStatusAndLockedUntilBefore(BookingStatus status, Instant now, Pageable pageable);

    /**
     * Magic method for user history, ordered by creation time descending.
     */
    List<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId);
}