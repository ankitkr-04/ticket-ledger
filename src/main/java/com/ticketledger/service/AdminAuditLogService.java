package com.ticketledger.service;

import java.util.UUID;

import com.ticketledger.constant.SecurityConstant;
import com.ticketledger.domain.entity.AdminAuditLog;
import com.ticketledger.domain.enums.AdminLogAction;
import com.ticketledger.domain.enums.AdminLogStatus;

public interface AdminAuditLogService {

    AdminAuditLog createRefundLog(UUID bookingId, UUID adminId, String reason, String idempotencyKey);

    void completeLog(UUID logId, String providerRefundId);

    void failLog(UUID logId, String errorReason);

    AdminAuditLog createShowtimeLog(UUID showtimeId, UUID adminId, AdminLogAction action, String reason,
            String idempotencyKey);

    /**
     * Logs a system or admin action.
     * If adminId is null, SecurityConstant.SYSTEM_ADMIN_ID is used.
     */
    void logAction(UUID adminId, UUID bookingId, AdminLogAction action, AdminLogStatus status, String reason);

    default void logSystemAction(UUID bookingId, AdminLogAction action, AdminLogStatus status, String reason) {
        logAction(SecurityConstant.SYSTEM_ADMIN_ID, bookingId, action, status, reason);
    }
}
