package com.ticketledger.service;

import java.util.UUID;

/**
 * Gatekeeper for Privileged Admin Operations.
 * <p>
 * This service enforces the Strict Ownership Model (Doc 006).
 * It validates that the currently authenticated admin has explicit access
 * to the target Theater (scope root) before allowing any write operation.
 * <p>
 * Usage:
 * Call {@code assertTheaterAccess(theaterId)} at the start of any
 * transactional admin service method.
 */
public interface AdminAuthorizationService {

    /**
     * Asserts that the currently authenticated user has access to the specified theater.
     * <p>
     * Retrieves the current user ID from the SecurityContext.
     *
     * @param theaterId the target theater ID
     * @return The UUID of the authorized Admin
     * @throws com.ticketledger.exception.domain.TheaterAccessDeniedException if access is denied
     */
    UUID assertTheaterAccess(UUID theaterId);

    /**
     * Asserts that the currently authenticated user has access to the theater owning the screen.
     * <p>
     * Retrieves the current user ID from the SecurityContext.
     *
     * @param screenId the target screen ID
     * @return The UUID of the authorized Admin
     * @throws com.ticketledger.exception.domain.TheaterAccessDeniedException if access is denied
     */
    UUID assertScreenAccess(UUID screenId);

    /**
     * Asserts that the currently authenticated user has access to the theater owning the showtime.
     * <p>
     * This is required for operations like "Pause Showtime".
     * Retrieves the current user ID from the SecurityContext.
     *
     * @param showtimeId the target showtime ID
     * @return The UUID of the authorized Admin
     * @throws com.ticketledger.exception.domain.TheaterAccessDeniedException if access is denied
     */
    UUID assertShowtimeAccess(UUID showtimeId);

    /**
     * Asserts that the currently authenticated user has access to the theater owning the booking.
     * <p>
     * This is required for operations like "Manual Refund".
     * Retrieves the current user ID from the SecurityContext.
     *
     * @param bookingId the target booking ID
     * @return The UUID of the authorized Admin
     * @throws com.ticketledger.exception.domain.TheaterAccessDeniedException if access is denied
     */
    UUID assertBookingAccess(UUID bookingId);
}
