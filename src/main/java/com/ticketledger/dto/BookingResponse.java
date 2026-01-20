package com.ticketledger.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ticketledger.domain.model.entity.Booking;
import com.ticketledger.domain.model.entity.BookingSeat;
import com.ticketledger.domain.model.entity.Payment;
import com.ticketledger.domain.model.enums.BookingStatus;
import com.ticketledger.domain.model.enums.PaymentProvider;
import com.ticketledger.domain.model.enums.PaymentStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingResponse(
                UUID bookingId,
                BookingStatus status,
                Instant expiresAt,
                Instant confirmedAt,
                Instant cancelledAt,
                List<SeatDetails> seats,
                AmountDetails amount,
                PaymentDetails payment,
                TicketDetails ticket) {

        public static BookingResponse fromEntity(Booking booking, List<BookingSeat> bookingSeats, Payment payment) {
                return fromEntity(booking, bookingSeats, payment, null, null);
        }

        public static BookingResponse fromEntity(Booking booking, List<BookingSeat> bookingSeats, Payment payment,
                        String clientSecret, String redirectUrl) {

                var amountDetails = new AmountDetails(
                                payment.getAmount(),
                                payment.getCurrency(),
                                bookingSeats.stream()
                                                .map(bs -> new SeatPriceBreakdown(bs.getSeat().getId(),
                                                                bs.getPriceAtBooking()))
                                                .toList());

                var paymentDetails = new PaymentDetails(
                                payment.getId(),
                                payment.getProvider(),
                                payment.getStatus(),
                                payment.getMethod(),
                                clientSecret,
                                redirectUrl,
                                payment.getProviderCapturedAt(),
                                null);

                List<SeatDetails> seatDTOs = bookingSeats.stream()
                                .map(bs -> new SeatDetails(
                                                bs.getSeat().getId(),
                                                bs.getSeat().getSeatRow(),
                                                bs.getSeat().getSeatNumber(),
                                                bs.getSeat().getTier().getName(),
                                                bs.getPriceAtBooking()))
                                .toList();

                return new BookingResponse(
                                booking.getId(),
                                booking.getStatus(),
                                booking.getLockedUntil(),
                                booking.getConfirmedAt(),
                                booking.getCancelledAt(),
                                seatDTOs,
                                amountDetails,
                                paymentDetails,
                                null);
        }

        public record AmountDetails(
                        BigDecimal total,
                        String currency,
                        List<SeatPriceBreakdown> breakdown) {
        }

        public record SeatPriceBreakdown(
                        UUID seatId,
                        BigDecimal price) {
        }

        public record PaymentDetails(
                        UUID paymentId,
                        PaymentProvider provider,
                        PaymentStatus status,
                        String method,
                        String clientSecret,
                        String redirectUrl,
                        Instant capturedAt,
                        Integer attemptNumber) {
        }

        public record TicketDetails(
                        String qrCode,
                        String ticketNumber) {
        }
}