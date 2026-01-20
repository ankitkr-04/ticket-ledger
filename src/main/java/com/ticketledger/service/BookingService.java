package com.ticketledger.service;

import java.util.UUID;

import com.ticketledger.dto.BookingResponse;
import com.ticketledger.dto.CreateBookingRequest;
import com.ticketledger.dto.PaymentWebhookRequest;

public interface BookingService {
    BookingResponse createBooking(CreateBookingRequest request, UUID userId, UUID idempotencyKey);

    void processPaymentWebhook(PaymentWebhookRequest request);

    void expireBooking(UUID bookingId);
}
