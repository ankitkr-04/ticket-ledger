package com.ticketledger.domain.enums;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents the lifecycle state of a booking.
 * Enforces valid state transitions per 002_lifecycle_states.md.
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
    REFUNDED;

    private static final Map<BookingStatus, Set<BookingStatus>> VALID_TRANSITIONS = new EnumMap<>(BookingStatus.class);

    static {
        VALID_TRANSITIONS.put(HELD, Set.of(CONFIRMED, EXPIRED));
        VALID_TRANSITIONS.put(CONFIRMED, Set.of(CANCELLED, SYSTEM_CANCELLED, REFUND_INITIATED, COMPLETED, REFUND_REQUIRED));
        VALID_TRANSITIONS.put(EXPIRED, Set.of(REFUND_REQUIRED, CONFIRMED));
        VALID_TRANSITIONS.put(CANCELLED, Set.of(REFUND_REQUIRED_MANUAL));
        VALID_TRANSITIONS.put(SYSTEM_CANCELLED, Set.of(REFUND_REQUIRED_MANUAL));
        VALID_TRANSITIONS.put(COMPLETED, Set.of(REFUND_INITIATED));
        VALID_TRANSITIONS.put(REFUND_INITIATED, Set.of(REFUNDED, REFUND_FAILED, CONFIRMED));
        // Terminal states: REFUNDED, REFUND_FAILED, REFUND_REQUIRED, REFUND_REQUIRED_MANUAL
        VALID_TRANSITIONS.put(REFUNDED, Set.of());
        VALID_TRANSITIONS.put(REFUND_FAILED, Set.of());
        VALID_TRANSITIONS.put(REFUND_REQUIRED, Set.of());
        VALID_TRANSITIONS.put(REFUND_REQUIRED_MANUAL, Set.of());
    }

    /**
     * Returns true if transitioning from this status to the given target is allowed.
     */
    public boolean canTransitionTo(BookingStatus target) {
        Set<BookingStatus> allowed = VALID_TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }
}
