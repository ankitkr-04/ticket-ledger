
package com.ticketledger.service.scheduler;

import java.time.Instant;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ticketledger.domain.entity.Booking;
import com.ticketledger.domain.enums.BookingStatus;
import com.ticketledger.domain.enums.PaymentStatus;
import com.ticketledger.domain.repository.BookingRepository;
import com.ticketledger.domain.repository.PaymentRepository;
import com.ticketledger.service.booking.BookingService;
import com.ticketledger.service.gateway.PaymentGateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingCleanupScheduler {
    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    @Scheduled(fixedDelayString = "${booking.cleanup-interval-ms:60000}")
    public void cleanupExpiredBookings() {
        // Architecture 010: 30-second safety buffer for clock drift/gateway lag
        Instant safetyThreshold = Instant.now().minusSeconds(30);

        var candidateBookings = bookingRepository.findByStatusAndLockedUntilBefore(
                BookingStatus.HELD,
                safetyThreshold,
                PageRequest.of(0, 50));

        if (candidateBookings.isEmpty())
            return;

        log.info("Processing {} cleanup candidates...", candidateBookings.size());

        for (var booking : candidateBookings) {
            try {
                processCleanup(booking);
            } catch (Exception e) {
                log.error("Cleanup failed for booking {}: {}", booking.getId(), e.getMessage());
            }
        }
    }

    private void processCleanup(Booking booking) {
        // Get the latest payment attempt
        var paymentOpt = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(booking.getId());

        // Path A: FAST PATH - No gateway transaction ever started
        if (paymentOpt.isEmpty() || paymentOpt.get().getProviderTransactionId() == null) {
            log.info("Fast-path expiring abandoned booking: {}", booking.getId());
            bookingService.expireBooking(booking.getId());
            return;
        }

        // Path B: VERIFICATION PATH - Check Stripe for late success
        String providerId = paymentOpt.get().getProviderTransactionId();
        PaymentStatus gatewayStatus = paymentGateway.verifyPaymentStatus(providerId);

        switch (gatewayStatus) {
            case SUCCESS -> {
                log.info("Late success detected for {}. Triggering reclamation (Bump).", booking.getId());
                bookingService.reclaimSeatForLatePayment(booking.getId());
            }
            case FAILED -> {
                log.info("Gateway confirmed failure for {}. Expiring hold.", booking.getId());
                bookingService.expireBooking(booking.getId());
            }
            case PENDING -> {
                // Do nothing. Wait for next run to give the user/webhook more time.
                log.debug("Payment for {} still pending at gateway. Skipping.", booking.getId());
            }

            default -> {
                log.warn("Unknown payment status {} for booking {}. Skipping.", gatewayStatus, booking.getId());
            }
        }
    }
}