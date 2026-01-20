package com.ticketledger.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BookingRefundEvent(
        UUID bookingId,
        String userEmail,
        BigDecimal amount,
        String reason,
        Instant occurredAt) {
}