package com.ticketledger.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ticketledger.domain.entity.Payment;
import com.ticketledger.domain.enums.PaymentStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Uses @Query to avoid Spring Data parsing "WithLock" as a property.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdWithLock(@Param("id") UUID id);

    Optional<Payment> findByBookingId(UUID bookingId);

    @Query("SELECT p FROM Payment p WHERE p.booking.id = :bookingId ORDER BY p.createdAt DESC")
    Optional<Payment> findFirstByBookingIdOrderByCreatedAtDesc(@Param("bookingId") UUID bookingId);

    @Query("SELECT p FROM Payment p WHERE p.booking.id = :bookingId AND p.status = :status")
    Optional<Payment> findFirstByBookingIdAndStatus(@Param("bookingId") UUID bookingId,
            @Param("status") PaymentStatus status);

}
