package com.ticketledger.domain.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ticketledger.domain.model.entity.IdempotencyKey;

@Repository
public interface IdempotencyKeyRepository
                extends JpaRepository<IdempotencyKey, UUID> {
        @Modifying
        @Query("DELETE FROM IdempotencyKey k WHERE k.expiresAt < :now")
        int deleteExpiredKeys(Instant now);

}
