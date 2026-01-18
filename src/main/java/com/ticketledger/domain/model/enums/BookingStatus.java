package com.ticketledger.domain.model.enums;

/**
 * Represents the lifecycle state of a booking.
 */
public enum BookingStatus {
    HELD,
    CONFIRMED,
    EXPIRED,
    CANCELLED,
    COMPLETED,
    REFUND_REQUIRED
}
