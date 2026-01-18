package com.ticketledger.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ticketledger.domain.model.enums.BookingStatus;
import com.ticketledger.domain.model.enums.PaymentStatus;

/**
 * Detailed response DTO for a specific booking.
 * Matches the JSON contract for POST /bookings and GET /bookings/{id}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingResponse(
        UUID bookingId,
        BookingStatus status,

        // Included only if the booking is currently HELD
        Instant expiresAt,

        // Included only if the booking is CONFIRMED or CANCELLED
        Instant confirmedAt,
        Instant cancelledAt,

        List<SeatDTO> seats,
        AmountDetails amount,
        PaymentDetails payment,

        // Included only upon successful confirmation
        TicketDetails ticket) {

    // --- Nested DTOs to match JSON Structure ---

    public record AmountDetails(
            BigDecimal total,
            String currency,
            // Breakdown is optional/nullable based on view depth
            List<SeatPriceBreakdown> breakdown) {
    }

    public record SeatPriceBreakdown(
            UUID seatId,
            BigDecimal price) {
    }

    public record PaymentDetails(
            UUID paymentId,
            String provider, // e.g., "STRIPE"
            PaymentStatus status,
            String method, // e.g., "CREDIT_CARD"

            // Fields specific to the payment flow (creating a hold)
            String clientSecret,
            String redirectUrl,

            // Fields specific to historical view
            Instant capturedAt,
            Integer attemptNumber) {
    }

    public record TicketDetails(
            String qrCode, // Base64 encoded image or raw string data
            String ticketNumber) {
    }
}