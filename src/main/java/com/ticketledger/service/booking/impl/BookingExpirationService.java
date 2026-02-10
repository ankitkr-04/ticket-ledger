package com.ticketledger.service.booking.impl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ticketledger.domain.entity.Booking;
import com.ticketledger.domain.entity.BookingSeat;
import com.ticketledger.domain.entity.Seat;
import com.ticketledger.domain.enums.BookingStatus;
import com.ticketledger.domain.enums.PaymentStatus;
import com.ticketledger.domain.enums.SeatStatus;
import com.ticketledger.domain.repository.BookingRepository;
import com.ticketledger.domain.repository.BookingSeatRepository;
import com.ticketledger.domain.repository.PaymentRepository;
import com.ticketledger.domain.repository.SeatRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingExpirationService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final SeatRepository seatRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireBooking(UUID bookingId) {
        var bookingOpt = bookingRepository.findByIdWithLock(bookingId);
        if (bookingOpt.isEmpty()) {
            return;
        }

        Booking booking = bookingOpt.get();

        if (booking.getStatus() != BookingStatus.HELD || booking.getLockedUntil().isAfter(Instant.now())) {
            return;
        }

        booking.setStatus(BookingStatus.EXPIRED);
        bookingRepository.save(booking);

        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(booking.getId());
        List<Seat> seatToRelease = bookingSeats.stream().map(BookingSeat::getSeat)
                .peek(seat -> seat.setStatus(SeatStatus.AVAILABLE)).toList();

        if (!seatToRelease.isEmpty()) {
            seatRepository.saveAll(seatToRelease);
        }

        paymentRepository.findByBookingId(booking.getId()).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
            }
        });
    }
}
