package com.ticketledger.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.ticketledger.config.BookingProperties;
import com.ticketledger.domain.model.entity.*;
import com.ticketledger.domain.model.enums.BookingStatus;
import com.ticketledger.domain.model.enums.PaymentProvider;
import com.ticketledger.domain.model.enums.PaymentStatus;
import com.ticketledger.domain.model.enums.SeatStatus;
import com.ticketledger.domain.repository.*;
import com.ticketledger.dto.BookingResponse;
import com.ticketledger.dto.CreateBookingRequest;
import com.ticketledger.exception.BusinessException;
import com.ticketledger.exception.SeatAlreadyBookedException;
import com.ticketledger.exception.ShowtimeNotFoundException;
import com.ticketledger.service.BookingService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

        private final SeatRepository seatRepository;
        private final BookingRepository bookingRepository;
        private final ShowtimeRepository showtimeRepository;
        private final UserRepository userRepository;
        private final PaymentRepository paymentRepository;
        private final BookingSeatRepository bookingSeatRepository;

        private final BookingProperties bookingProperties;

        @Override
        @Transactional(isolation = Isolation.READ_COMMITTED)
        @Retryable(includes = PessimisticLockingFailureException.class, maxRetries = 3)
        public BookingResponse createBooking(CreateBookingRequest request, UUID userId) {

                // 1. Sort Seat IDs (CRITICAL: Deadlock Prevention)
                List<UUID> sortedSeatIds = request.seatIds().stream()
                                .sorted()
                                .toList();

                // 2. Acquire Locks (PESSIMISTIC_WRITE)
                List<Seat> lockedSeats = seatRepository.lockSeats(sortedSeatIds);

                // 3. Validate State (Check availability & mismatch)
                validateSeats(sortedSeatIds, lockedSeats, request.showtimeId());

                // 4. Validate Showtime (Fetch + Check)
                // Note: showtime.checkBookable() throws specific Domain Exceptions
                // (ShowtimeClosed/Expired)
                Showtime showtime = showtimeRepository.findById(request.showtimeId())
                                .orElseThrow(() -> new ShowtimeNotFoundException(request.showtimeId()));

                showtime.checkBookable();

                // 5. Fetch User
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(
                                                "User not found",
                                                "USER_NOT_FOUND",
                                                HttpStatus.NOT_FOUND,
                                                Map.of("userId", userId)));

                // 6. Logic: Prepare Data
                Instant now = Instant.now();
                Instant expiresAt = now.plus(bookingProperties.lockDurationMinutes(), ChronoUnit.MINUTES);

                // Create Booking Header
                Booking booking = new Booking();
                booking.setUser(user);
                booking.setShowtime(showtime);
                booking.setStatus(BookingStatus.HELD);
                booking.setLockedUntil(expiresAt);
                booking = bookingRepository.save(booking);

                BigDecimal totalAmount = BigDecimal.ZERO;
                List<BookingSeat> bookingSeats = new ArrayList<>();

                // Process Seats
                for (Seat seat : lockedSeats) {
                        seat.setStatus(SeatStatus.HELD);

                        BigDecimal seatPrice = calculatePrice(seat);
                        totalAmount = totalAmount.add(seatPrice);

                        // Constructor uses standard Entity references as per previous fix
                        BookingSeat bookingSeat = new BookingSeat(booking, seat, seatPrice);
                        bookingSeats.add(bookingSeat);
                }

                // Persist Changes (Batch Save)
                seatRepository.saveAll(lockedSeats);
                bookingSeatRepository.saveAll(bookingSeats);

                // 7. Create Payment Intent
                Payment payment = createPendingPayment(booking, totalAmount);

                // 8. Map Response
                // Note: Uses the overloaded method from BookingResponse which accepts null
                // secrets for this internal view
                return BookingResponse.fromEntity(booking, bookingSeats, payment);
        }

        // --- HELPER METHODS ---

        private BigDecimal calculatePrice(Seat seat) {
                return bookingProperties.defaultBasePrice().multiply(seat.getTier().getPriceMultiplier());
        }

        private Payment createPendingPayment(Booking booking, BigDecimal amount) {
                Payment payment = new Payment();
                payment.setBooking(booking);
                payment.setAmount(amount);
                payment.setCurrency(bookingProperties.currency());
                payment.setProvider(PaymentProvider.STRIPE);
                payment.setStatus(PaymentStatus.PENDING);
                return paymentRepository.save(payment);
        }

        private void validateSeats(List<UUID> requestedIds, List<Seat> lockedSeats, UUID requestShowtimeId) {
                if (lockedSeats.size() != requestedIds.size()) {
                        List<UUID> foundIds = lockedSeats.stream().map(Seat::getId).toList();
                        List<UUID> missing = requestedIds.stream()
                                        .filter(id -> !foundIds.contains(id))
                                        .toList();
                        throw new BusinessException(
                                        "Seats not found", "SEATS_NOT_FOUND",
                                        HttpStatus.BAD_REQUEST, Map.of("missingIds", missing));
                }

                List<UUID> unavailableIds = lockedSeats.stream()
                                .filter(s -> s.getStatus() != SeatStatus.AVAILABLE)
                                .map(Seat::getId)
                                .toList();

                if (!unavailableIds.isEmpty()) {
                        throw new SeatAlreadyBookedException(unavailableIds);
                }

                boolean allMatchShowtime = lockedSeats.stream()
                                .allMatch(s -> s.getShowtime().getId().equals(requestShowtimeId));

                if (!allMatchShowtime) {
                        throw new BusinessException(
                                        "Seats mismatch showtime", "INVALID_SHOWTIME_SEATS",
                                        HttpStatus.BAD_REQUEST,
                                        Map.of("requestShowtimeId", requestShowtimeId));
                }
        }
}