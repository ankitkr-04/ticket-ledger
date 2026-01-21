package com.ticketledger.service;

import java.util.UUID;

import com.ticketledger.domain.entity.AdminAuditLog;

import com.ticketledger.domain.enums.AdminLogAction;

public interface AdminAuditLogService {

    AdminAuditLog createRefundLog(UUID bookingId, UUID adminId, String reason, String idempotencyKey);

    void completeLog(UUID logId, String providerRefundId);

    void failLog(UUID logId, String errorReason);

    AdminAuditLog createShowtimeLog(UUID showtimeId, UUID adminId, AdminLogAction action, String reason, String idempotencyKey);
}
