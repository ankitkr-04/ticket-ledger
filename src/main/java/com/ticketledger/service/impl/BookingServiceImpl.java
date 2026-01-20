package com.ticketledger.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketledger.config.BookingProperties;
import com.ticketledger.domain.model.entity.*;
import com.ticketledger.domain.model.enums.BookingStatus;
import com.ticketledger.domain.model.enums.PaymentProvider;
import com.ticketledger.domain.model.enums.PaymentStatus;
import com.ticketledger.domain.model.enums.SeatStatus;
import com.ticketledger.domain.repository.*;
import com.ticketledger.dto.BookingResponse;
import com.ticketledger.dto.CreateBookingRequest;
import com.ticketledger.dto.PaymentWebhookRequest;
import com.ticketledger.exception.BusinessException;
import com.ticketledger.exception.SeatAlreadyBookedException;
import com.ticketledger.exception.ShowtimeNotFoundException;
import com.ticketledger.service.BookingService;
import com.ticketledger.service.IdempotencyService;

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

        private final IdempotencyService idempotencyService;
        private final ObjectMapper objectMapper;

        @Override
        @Transactional(isolation = Isolation.REPEATABLE_READ)
        public void processPaymentWebhook(PaymentWebhookRequest request) {
                // 1. Lock Payment (prevents duplicate webhook processing)
                var paymentOpt = paymentRepository.findByIdWithLock(request.paymentId());
                if (paymentOpt.isEmpty()) {
                        throw new BusinessException(
                                        "Payment not found",
                                        "PAYMENT_NOT_FOUND",
                                        HttpStatus.NOT_FOUND,
                                        Map.of("paymentId", request.paymentId()));
                }

                Payment payment = paymentOpt.get();

                // Idempotent: already processed successfully
                if (payment.getStatus() == PaymentStatus.SUCCESS) {
                        return;
                }

                // 2. Lock Booking (CRITICAL: prevents race with Reaper job)
                // Without this lock, Reaper could expire the booking while we're processing
                Booking booking = bookingRepository.findByIdWithLock(payment.getBooking().getId())
                                .orElseThrow(() -> new BusinessException(
                                                "Booking not found",
                                                "BOOKING_NOT_FOUND",
                                                HttpStatus.NOT_FOUND,
                                                Map.of("bookingId", payment.getBooking().getId())));

                // 3. Handle Payment Success
                if (request.status() == PaymentStatus.SUCCESS) {
                        payment.setStatus(PaymentStatus.SUCCESS);
                        paymentRepository.save(payment);

                        if (booking.getStatus() == BookingStatus.HELD) {
                                // Normal flow: confirm the booking
                                booking.setStatus(BookingStatus.CONFIRMED);
                                bookingRepository.save(booking);

                                // Mark seats as SOLD
                                List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(booking.getId());
                                for (BookingSeat bs : bookingSeats) {
                                        Seat seat = bs.getSeat();
                                        seat.setStatus(SeatStatus.SOLD);
                                        seatRepository.save(seat);
                                }

                        } else if (booking.getStatus() == BookingStatus.EXPIRED) {
                                // Edge case: payment succeeded but booking already expired (Reaper ran)
                                // Re-validate seat availability with locks
                                List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(booking.getId());
                                List<UUID> seatIds = bookingSeats.stream()
                                                .map(bs -> bs.getSeat().getId())
                                                .sorted() // Deadlock prevention
                                                .toList();

                                // Lock seats to check current state (prevents reading stale data)
                                List<Seat> lockedSeats = seatRepository.lockSeats(seatIds);

                                // Check if all seats are still available
                                boolean allAvailable = lockedSeats.stream()
                                                .allMatch(seat -> seat.getStatus() == SeatStatus.AVAILABLE);

                                if (allAvailable) {
                                        // All seats available: re-acquire and confirm booking
                                        for (Seat seat : lockedSeats) {
                                                seat.setStatus(SeatStatus.SOLD);
                                                seatRepository.save(seat);
                                        }
                                        booking.setStatus(BookingStatus.CONFIRMED);
                                        bookingRepository.save(booking);
                                } else {
                                        // Some seats unavailable (sold to someone else): mark for refund
                                        booking.setStatus(BookingStatus.REFUND_REQUIRED);
                                        bookingRepository.save(booking);
                                }
                        }

                } else if (request.status() == PaymentStatus.FAILED) {
                        // 4. Handle Payment Failure
                        // Mark payment as failed, but do NOT cancel booking or release seats
                        // Rationale: User can retry with another card while booking is still held
                        // The Reaper job will expire the booking if user doesn't retry in time
                        payment.setStatus(PaymentStatus.FAILED);
                        paymentRepository.save(payment);
                }
        }

        @Transactional(isolation = Isolation.READ_COMMITTED)
        @Retryable(includes = PessimisticLockingFailureException.class, maxRetries = 3)
        @Override
        public BookingResponse createBooking(CreateBookingRequest request, UUID userId, UUID idempotencyKey) {

                String requestHash = generateRequestHash(request, userId);

                boolean isLockAcquired = idempotencyService.lock(idempotencyKey, userId, requestHash);

                if (!isLockAcquired) {
                        return handleIdempotencyHit(idempotencyKey);
                }

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
                var response = BookingResponse.fromEntity(booking, bookingSeats, payment);

                JsonNode requestJson = objectMapper.valueToTree(response);
                idempotencyService.saveResponse(idempotencyKey, HttpStatus.CREATED.value(), requestJson);
                return response;
        }

        @Override
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

        // --- HELPER METHODS ---

        private String generateRequestHash(CreateBookingRequest request, UUID userId) {
                try {
                        // Combine request data with userId for hashing
                        var combined = Map.of(
                                        "userId", userId,
                                        "request", request);
                        String jsonString = objectMapper.writeValueAsString(combined);
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        byte[] hashBytes = digest.digest(jsonString.getBytes(StandardCharsets.UTF_8));

                        return HexFormat.of().formatHex(hashBytes);
                } catch (Exception e) {
                        throw new BusinessException(
                                        "Failed to generate request hash",
                                        "REQUEST_HASH_FAILURE",
                                        HttpStatus.INTERNAL_SERVER_ERROR);
                }
        }

        private BookingResponse handleIdempotencyHit(UUID idempotencyKey) {
                var existingKeyOpt = idempotencyService.findKey(idempotencyKey);
                if (existingKeyOpt.isEmpty() || existingKeyOpt.get().getResponseStatus() == null
                                || existingKeyOpt.get().getResponseBody() == null) {
                        throw new BusinessException(
                                        "Idempotent request in progress",
                                        "IDEMPOTENCY_IN_PROGRESS",
                                        HttpStatus.CONFLICT);
                }

                var existingKey = existingKeyOpt.get();
                try {
                        return objectMapper.treeToValue(existingKey.getResponseBody(), BookingResponse.class);
                } catch (Exception e) {
                        throw new BusinessException(
                                        "Failed to deserialize idempotent response",
                                        "IDEMPOTENCY_RESPONSE_DESERIALIZATION_FAILURE",
                                        HttpStatus.INTERNAL_SERVER_ERROR);
                }
        }

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