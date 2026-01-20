package com.ticketledger.domain.entity;

import java.time.LocalDateTime;

import com.ticketledger.domain.base.BaseEntity;
import com.ticketledger.domain.enums.AdminLogAction;
import com.ticketledger.domain.enums.AdminLogStatus;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "admin_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private User adminUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminLogAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminLogStatus status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "stripe_refund_id")
    private String stripeRefundId;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}