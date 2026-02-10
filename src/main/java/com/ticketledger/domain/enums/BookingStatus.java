package com.ticketledger.domain.enums;

/**
 * Represents the lifecycle state of a booking.
 */
public enum BookingStatus {
    HELD,
    CONFIRMED,
    EXPIRED,
    CANCELLED,
    SYSTEM_CANCELLED,
    COMPLETED,
    REFUND_REQUIRED,
    REFUND_REQUIRED_MANUAL,
    REFUND_INITIATED,
    REFUND_FAILED,
    REFUNDED
}
