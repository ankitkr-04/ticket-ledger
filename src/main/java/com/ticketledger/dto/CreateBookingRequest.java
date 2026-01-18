package com.ticketledger.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for initiating a booking.
 * Utilizes Jakarta Validation to enforce business rules at the controller entry
 * point.
 */
public record CreateBookingRequest(

        @NotNull(message = "Showtime ID is required") UUID showtimeId,

        @NotEmpty(message = "At least one seat must be selected") @Size(min = 1, max = 10, message = "Cannot book more than 10 seats in a single transaction") List<@NotNull(message = "Seat ID cannot be null") UUID> seatIds) {
}