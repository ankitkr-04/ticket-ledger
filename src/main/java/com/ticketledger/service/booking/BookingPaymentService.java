package com.ticketledger.service.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.ticketledger.domain.entity.Booking;
import com.ticketledger.domain.entity.BookingSeat;
import com.ticketledger.domain.entity.Payment;
import com.ticketledger.domain.entity.Seat;
import com.ticketledger.domain.enums.BookingStatus;
import com.ticketledger.domain.enums.PaymentStatus;
import com.ticketledger.domain.enums.SeatStatus;
import com.ticketledger.domain.event.BookingConfirmedEvent;
import com.ticketledger.domain.repository.BookingRepository;
import com.ticketledger.domain.repository.BookingSeatRepository;
import com.ticketledger.domain.repository.PaymentRepository;
import com.ticketledger.domain.repository.SeatRepository;
import com.ticketledger.dto.PaymentWebhookRequest;
import com.ticketledger.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingPaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final SeatRepository seatRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SeatReclamationService seatReclamationService;

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void processPaymentWebhook(PaymentWebhookRequest request) {
        Payment payment = paymentRepository.findByIdWithLock(request.paymentId())
                .orElseThrow(() -> new BusinessException(
                        "Payment not found",
                        "PAYMENT_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        Map.of("paymentId", request.paymentId())));

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return;
        }

        Booking booking = bookingRepository.findByIdWithLock(payment.getBooking().getId())
                .orElseThrow(() -> new BusinessException(
                        "Booking not found",
                        "BOOKING_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        Map.of("bookingId", payment.getBooking().getId())));

        if (request.status() == PaymentStatus.SUCCESS) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setProviderTransactionId(request.providerTransactionId());
            payment.setProviderCapturedAt(Instant.now());
            paymentRepository.save(payment);

            if (booking.getStatus() == BookingStatus.HELD) {
                confirmBooking(booking);
            } else if (booking.getStatus() == BookingStatus.EXPIRED) {
                handleExpiredBookingLatePayment(booking, payment);
            }

        } else if (request.status() == PaymentStatus.FAILED) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
        }
    }

    private void confirmBooking(Booking booking) {
        booking.transitionTo(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(booking.getId());
        bookingSeats.forEach(bs -> {
            Seat seat = bs.getSeat();
            seat.setStatus(SeatStatus.SOLD);
            seatRepository.save(seat);
        });

        eventPublisher.publishEvent(new BookingConfirmedEvent(
                booking.getId(),
                booking.getUser().getEmail(),
                bookingSeats.stream().map(BookingSeat::getPriceAtBooking).reduce(BigDecimal.ZERO,
                        BigDecimal::add),
                Instant.now()));
    }

    private void handleExpiredBookingLatePayment(Booking booking, Payment payment) {
        seatReclamationService.reclaimOrBumpSeats(booking.getId());
    }
}
