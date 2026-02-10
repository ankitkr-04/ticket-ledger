package com.ticketledger.service.booking;

import java.util.UUID;

import com.ticketledger.dto.BookingResponse;
import com.ticketledger.dto.CreateBookingRequest;
import com.ticketledger.dto.PaymentWebhookRequest;
import com.ticketledger.dto.RefundResponse;

public interface BookingService {
    BookingResponse createBooking(CreateBookingRequest request, UUID userId, UUID idempotencyKey);

    void processPaymentWebhook(PaymentWebhookRequest request);

    void expireBooking(UUID bookingId);

    RefundResponse processAdminRefund(UUID bookingId, String reason, UUID adminId, String idempotencyKey);

    void reclaimSeatForLatePayment(UUID bookingId);
}
