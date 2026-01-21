package com.ticketledger.service;

import java.util.UUID;

import com.ticketledger.domain.entity.AdminAuditLog;

public interface AdminAuditLogService {

    AdminAuditLog createRefundLog(UUID bookingId, UUID adminId, String reason, String idempotencyKey);

    void completeLog(UUID logId, String providerRefundId);

    void failLog(UUID logId, String errorReason);
}
