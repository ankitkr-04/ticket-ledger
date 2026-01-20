package com.ticketledger.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BookingConfirmedEvent(
        UUID bookingId,
        String userEmail,
        BigDecimal amount,
        Instant confirmedAt) {
}