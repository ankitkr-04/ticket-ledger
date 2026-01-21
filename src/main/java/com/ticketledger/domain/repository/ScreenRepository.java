package com.ticketledger.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ticketledger.domain.entity.Screen;

@Repository
public interface ScreenRepository extends JpaRepository<Screen, UUID> {

    @Query("SELECT s.theater.id FROM Screen s WHERE s.id = :id")
    java.util.Optional<UUID> findTheaterIdById(@org.springframework.data.repository.query.Param("id") UUID id);
}
