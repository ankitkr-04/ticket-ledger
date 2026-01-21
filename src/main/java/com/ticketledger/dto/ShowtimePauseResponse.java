package com.ticketledger.dto;

import java.util.UUID;

/**
 * Response details after pausing a showtime.
 */
public record ShowtimePauseResponse(
        UUID showtimeId,
        String status,
        int affectedBookings,
        int seatsReleased) {
}
