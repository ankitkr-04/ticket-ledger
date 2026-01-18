package com.ticketledger.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Shared DTO representing Seat details in responses.
 * Used in BookingResponse and AvailableSeatsResponse.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SeatDTO(
        UUID seatId,
        String row,
        String number,
        String tier,

        // Price might be null if viewing a booking history summary where breakdown
        // isn't loaded
        BigDecimal price,

        // Status is useful for the seat map, but might be omitted in a confirmed
        // booking receipt
        String status) {
    // specific constructor for Booking context where status might not be relevant
    public SeatDTO(UUID seatId, String row, String number, String tier, BigDecimal price) {
        this(seatId, row, number, tier, price, null);
    }
}