package com.ticketledger.domain.model.key;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for the BookingSeat junction table.
 */
public record BookingSeatId(UUID bookingId, UUID seatId) implements Serializable {}
