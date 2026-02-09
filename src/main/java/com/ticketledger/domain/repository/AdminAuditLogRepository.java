package com.ticketledger.domain.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ticketledger.domain.entity.AdminAuditLog;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {
    Optional<AdminAuditLog> findByIdempotencyKey(String idempotencyKey);

    /**
     * Finds ONE stuck INITIATED log older than threshold and locks it.
     * Uses native query for FOR UPDATE SKIP LOCKED.
     * Returns Optional to support one-at-a-time processing without holding
     * connection.
     */
    @Query(value = "SELECT * FROM admin_audit_log WHERE status = 'INITIATED' AND completed_at IS NULL AND created_at < :threshold ORDER BY created_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<AdminAuditLog> findNextStuckJobWithLock(Instant threshold);
}
