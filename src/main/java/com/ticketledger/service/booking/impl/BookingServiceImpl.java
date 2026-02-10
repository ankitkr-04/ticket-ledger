package com.ticketledger.service.booking.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ticketledger.dto.BookingResponse;
import com.ticketledger.dto.CreateBookingRequest;
import com.ticketledger.dto.PaymentWebhookRequest;
import com.ticketledger.dto.RefundResponse;
import com.ticketledger.service.booking.BookingService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingCreationService bookingCreationService;
    private final BookingPaymentService bookingPaymentService;
    private final BookingExpirationService bookingExpirationService;
    private final BookingRefundService bookingRefundService;
    private final SeatReclamationService seatReclamationService;

    @Override
    public BookingResponse createBooking(CreateBookingRequest request, UUID userId, UUID idempotencyKey) {
        return bookingCreationService.createBooking(request, userId, idempotencyKey);
    }

    @Override
    public void processPaymentWebhook(PaymentWebhookRequest request) {
        bookingPaymentService.processPaymentWebhook(request);
    }

    @Override
    public void expireBooking(UUID bookingId) {
        bookingExpirationService.expireBooking(bookingId);
    }

    @Override
    public RefundResponse processAdminRefund(UUID bookingId, String reason, UUID adminId, String idempotencyKey) {
        return bookingRefundService.processAdminRefund(bookingId, reason, adminId, idempotencyKey);
    }

    @Override
    public void reclaimSeatForLatePayment(UUID bookingId) {
        seatReclamationService.reclaimSeatForLatePayment(bookingId);
    }
}
