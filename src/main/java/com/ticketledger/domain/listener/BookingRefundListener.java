package com.ticketledger.domain.listener;

import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ticketledger.domain.entity.Payment;
import com.ticketledger.domain.enums.AdminLogAction;
import com.ticketledger.domain.enums.AdminLogStatus;
import com.ticketledger.domain.enums.BookingStatus;
import com.ticketledger.domain.enums.PaymentStatus;
import com.ticketledger.domain.event.BookingRefundEvent;
import com.ticketledger.domain.repository.BookingRepository;
import com.ticketledger.domain.repository.PaymentRepository;
import com.ticketledger.service.AdminAuditLogService;
import com.ticketledger.service.gateway.PaymentGateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingRefundListener {

    private final PaymentGateway paymentGateway;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final AdminAuditLogService adminAuditLogService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRefundEvent(BookingRefundEvent event) {
        log.info("Processing async refund for bumped booking: {}", event.bookingId());

        try {
            // 1. Find the successful payment that needs to be reversed
            Payment payment = paymentRepository.findFirstByBookingIdAndStatus(
                    event.bookingId(), PaymentStatus.SUCCESS)
                    .orElseThrow(() -> new IllegalStateException(
                            "No successful payment found to refund for booking " + event.bookingId()));

            // 2. Call Stripe Gateway (Using actual amount from the ledger)
            var response = paymentGateway.refundPayment(
                    payment.getProviderTransactionId(),
                    payment.getAmount(),
                    "reclaim-refund-" + event.bookingId());

            // 3. Update Financial Ledger
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setProviderRefundId(response.providerRefundId());
            paymentRepository.save(payment);

            log.info("Refund successful for booking {}. Refund ID: {}", event.bookingId(), response.providerRefundId());

        } catch (Exception e) {
            log.error("Automated reclamation refund failed for booking {}: {}", event.bookingId(), e.getMessage());
            handleRefundFailure(event.bookingId(), e.getMessage());
        }
    }

    private void handleRefundFailure(UUID bookingId, String error) {
        // Architecture 010: Transition to REFUND_REQUIRED_MANUAL for Admin visibility
        bookingRepository.findById(bookingId).ifPresent(booking -> {
            booking.setStatus(BookingStatus.REFUND_REQUIRED_MANUAL);
            bookingRepository.save(booking);

            // Create "Financial Debt" audit entry
            adminAuditLogService.logSystemAction(
                    bookingId,
                    AdminLogAction.AUTO_RECLAMATION_REFUND_FAILED,
                    AdminLogStatus.FAILED,
                    "Automated refund failed. Manual intervention required. Reason: " + error);
        });
    }
}