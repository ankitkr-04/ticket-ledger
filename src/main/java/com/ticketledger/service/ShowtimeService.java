package com.ticketledger.service;

import java.util.UUID;

import com.ticketledger.dto.ShowtimePauseResponse;

public interface ShowtimeService {

    /**
     * Pauses a showtime and cancels all HELD bookings.
     * 
     * @param showtimeId     ID of the showtime to pause
     * @param reason         Reason for the pause (for audit)
     * @param adminId        ID of the admin performing the action
     * @param idempotencyKey Idempotency key for the operation
     * @return Statistics about the operation
     */
    ShowtimePauseResponse pauseShowtime(UUID showtimeId, String reason, UUID adminId, String idempotencyKey);
}
