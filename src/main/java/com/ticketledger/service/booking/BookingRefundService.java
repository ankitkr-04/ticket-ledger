package com.ticketledger.service.booking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ticketledger.domain.entity.Booking;
import com.ticketledger.domain.entity.BookingSeat;
import com.ticketledger.domain.entity.Payment;
import com.ticketledger.domain.entity.Seat;
import com.ticketledger.domain.enums.BookingStatus;
import com.ticketledger.domain.enums.PaymentStatus;
import com.ticketledger.domain.enums.SeatStatus;
import com.ticketledger.domain.event.BookingRefundEvent;
import com.ticketledger.domain.repository.BookingRepository;
import com.ticketledger.domain.repository.BookingSeatRepository;
import com.ticketledger.domain.repository.PaymentRepository;
import com.ticketledger.domain.repository.SeatRepository;
import com.ticketledger.dto.RefundResponse;
import com.ticketledger.exception.BusinessException;
import com.ticketledger.service.AdminAuditLogService;
import com.ticketledger.service.gateway.PaymentGateway;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingRefundService {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final SeatRepository seatRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final TransactionTemplate transactionTemplate;
    private final PaymentGateway paymentGateway;
    private final AdminAuditLogService adminAuditLogService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public RefundResponse processAdminRefund(UUID bookingId, String reason, UUID adminId,
            String idempotencyKey) {

        record RefundContext(Payment payment, UUID logId) {
        }

        RefundContext context = transactionTemplate.execute(status -> {
            Booking booking = bookingRepository.findByIdWithLock(bookingId)
                    .orElseThrow(() -> new BusinessException("Booking not found",
                            "BOOKING_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (booking.getStatus() != BookingStatus.CONFIRMED
                    && booking.getStatus() != BookingStatus.COMPLETED) {
                throw new BusinessException("Booking not in refundable state", "INVALID_REFUND_STATE",
                        HttpStatus.BAD_REQUEST);
            }

            booking.transitionTo(BookingStatus.REFUND_INITIATED);
            bookingRepository.save(booking);

            var log = adminAuditLogService.createRefundLog(bookingId, adminId, reason, idempotencyKey);

            Payment payment = paymentRepository.findByBookingId(booking.getId())
                    .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                    .orElseThrow(() -> new BusinessException(
                            "No successful payment found to refund", "PAYMENT_NOT_FOUND",
                            HttpStatus.BAD_REQUEST));

            return new RefundContext(payment, log.getId());
        });

        RefundResponse refundResponse;
        try {
            refundResponse = paymentGateway.refundPayment(
                    context.payment().getProviderTransactionId(),
                    context.payment().getAmount(),
                    idempotencyKey);
        } catch (Exception e) {
            transactionTemplate.execute(status -> {
                Booking booking = bookingRepository.findByIdWithLock(bookingId).orElseThrow();
                booking.transitionTo(BookingStatus.CONFIRMED);
                bookingRepository.save(booking);

                adminAuditLogService.failLog(context.logId(), e.getMessage());
                return null;
            });
            throw e;
        }

        transactionTemplate.execute(status -> {
            Booking booking = bookingRepository.findByIdWithLock(bookingId).orElseThrow();
            booking.transitionTo(BookingStatus.REFUNDED);
            bookingRepository.save(booking);

            List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);
            bookingSeats.forEach(bs -> {
                Seat seat = bs.getSeat();
                seat.setStatus(SeatStatus.AVAILABLE);
                seatRepository.save(seat);
            });

            adminAuditLogService.completeLog(context.logId(), refundResponse.providerRefundId());

            eventPublisher.publishEvent(new BookingRefundEvent(
                    booking.getId(),
                    booking.getUser().getEmail(),
                    refundResponse.amount(),
                    reason,
                    Instant.now()));
            return null;
        });

        return refundResponse;
    }
}
