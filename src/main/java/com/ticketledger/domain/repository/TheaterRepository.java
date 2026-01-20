package com.ticketledger.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ticketledger.domain.entity.Theater;

/**
 * Repository for Theater entity operations.
 */
@Repository
public interface TheaterRepository extends JpaRepository<Theater, UUID> {

    /**
     * Find theaters by city
     */
    List<Theater> findByCity(String city);

    /**
     * Find theaters by name containing (case-insensitive)
     */
    List<Theater> findByNameContainingIgnoreCase(String name);
}
