package com.ticketledger.domain.enums;

/**
 * Represents the state of a payment transaction.
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REFUND_PENDING,
    REFUND_FAILED,
    REFUNDED
}
