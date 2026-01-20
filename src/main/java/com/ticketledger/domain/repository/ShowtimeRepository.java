package com.ticketledger.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ticketledger.domain.entity.Showtime;

@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, UUID> {

}