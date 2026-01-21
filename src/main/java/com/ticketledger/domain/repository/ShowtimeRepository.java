package com.ticketledger.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ticketledger.domain.entity.Showtime;

import jakarta.persistence.LockModeType;

@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, UUID> {

    @Query("SELECT s.screen.theater.id FROM Showtime s WHERE s.id = :showtimeId")
    Optional<UUID> findTheaterIdById(@Param("showtimeId") UUID showtimeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Showtime s WHERE s.id = :id")
    Optional<Showtime> findByIdWithLock(@Param("id") UUID id);
}