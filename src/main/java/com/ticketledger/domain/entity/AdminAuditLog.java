package com.ticketledger.domain.entity;

import java.time.Instant;

import com.ticketledger.domain.base.BaseEntity;
import com.ticketledger.domain.enums.AdminLogAction;
import com.ticketledger.domain.enums.AdminLogStatus;
import com.ticketledger.domain.enums.PaymentProvider;

import jakarta.persistence.*;
import lombok.*;

/**
 * Immutable audit trail for all privileged admin operations.
 * Supports forensics, idempotency, and reconciliation.
 */
@Entity
@Table(name = "admin_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAuditLog extends BaseEntity {

    // 🎯 TARGETS (at least one must be specified)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "showtime_id")
    private Showtime showtime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id")
    private Theater theater;

    // 👮 ACTOR
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private User adminUser;

    // 📝 ACTION
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AdminLogAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AdminLogStatus status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    // 🛡️ SAFETY
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    // 💳 PROVIDER CONTEXT (for payment-related actions)
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 50)
    private PaymentProvider provider;

    @Column(name = "provider_refund_id", length = 100)
    private String providerRefundId;

    // ⏱️ TIMESTAMPS
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Mark the audit log as completed successfully
     */
    public void markCompleted(PaymentProvider provider, String providerRefundId) {
        this.status = AdminLogStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.provider = provider;
        this.providerRefundId = providerRefundId;
    }

    /**
     * Mark the audit log as failed
     */
    public void markFailed() {
        this.status = AdminLogStatus.FAILED;
        this.completedAt = Instant.now();
    }

    /**
     * Check if this audit log has at least one target
     */
    public boolean hasTarget() {
        return booking != null || showtime != null || theater != null;
    }
}