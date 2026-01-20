package com.ticketledger.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import com.ticketledger.domain.model.entity.Payment;

import jakarta.persistence.LockModeType;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findByIdWithLock(UUID id);

    Optional<Payment> findByBookingId(UUID bookingId);

}
