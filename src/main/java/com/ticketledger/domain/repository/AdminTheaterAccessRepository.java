package com.ticketledger.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ticketledger.domain.entity.AdminTheaterAccess;
import com.ticketledger.domain.entity.Theater;
import com.ticketledger.domain.entity.User;

/**
 * Repository for AdminTheaterAccess entity operations.
 */
@Repository
public interface AdminTheaterAccessRepository extends JpaRepository<AdminTheaterAccess, UUID> {

    /**
     * Check if a user has access to a specific theater (active only)
     */
    @Query("SELECT COUNT(ata) > 0 FROM AdminTheaterAccess ata WHERE ata.user.id = :userId AND ata.theater.id = :theaterId AND ata.revokedAt IS NULL")
    boolean existsByUserIdAndTheaterId(@Param("userId") UUID userId, @Param("theaterId") UUID theaterId);

    /**
     * Find all theaters accessible by a user (active access only)
     */
    @Query("SELECT ata.theater FROM AdminTheaterAccess ata WHERE ata.user.id = :userId AND ata.revokedAt IS NULL")
    List<Theater> findTheatersByUserId(@Param("userId") UUID userId);

    /**
     * Find all admins with access to a theater (active access only)
     */
    @Query("SELECT ata.user FROM AdminTheaterAccess ata WHERE ata.theater.id = :theaterId AND ata.revokedAt IS NULL")
    List<User> findAdminsByTheaterId(@Param("theaterId") UUID theaterId);

    /**
     * Find access record by user and theater (active or revoked)
     */
    @Query("SELECT ata FROM AdminTheaterAccess ata WHERE ata.user.id = :userId AND ata.theater.id = :theaterId")
    AdminTheaterAccess findByUserIdAndTheaterId(@Param("userId") UUID userId, @Param("theaterId") UUID theaterId);

    /**
     * Delete access by user and theater
     */
    void deleteByUserIdAndTheaterId(UUID userId, UUID theaterId);

    /**
     * Count how many theaters an admin has access to
     */
    long countByUserId(UUID userId);
}
