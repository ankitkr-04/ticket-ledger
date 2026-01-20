package com.ticketledger.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketledger.domain.entity.AdminAuditLog;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {
    Optional<AdminAuditLog> findByIdempotencyKey(String idempotencyKey);
}