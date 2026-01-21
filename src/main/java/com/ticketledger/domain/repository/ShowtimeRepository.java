package com.ticketledger.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ticketledger.domain.entity.Showtime;

@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, UUID> {

    @Query("SELECT s.theater.id FROM Showtime sh JOIN sh.screen s WHERE sh.id = :id")
    java.util.Optional<UUID> findTheaterIdById(@Param("id") UUID id);
}