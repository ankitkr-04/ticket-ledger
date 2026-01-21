package com.ticketledger.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ticketledger.config.BookingProperties;
import com.ticketledger.domain.entity.*;
import com.ticketledger.domain.enums.BookingStatus;
import com.ticketledger.domain.enums.PaymentProvider;
import com.ticketledger.domain.enums.PaymentStatus;
import com.ticketledger.domain.enums.SeatStatus;
import com.ticketledger.domain.event.BookingConfirmedEvent;
import com.ticketledger.domain.event.BookingRefundEvent;
import com.ticketledger.domain.repository.*;
import com.ticketledger.dto.BookingResponse;
import com.ticketledger.dto.CreateBookingRequest;
import com.ticketledger.dto.PaymentWebhookRequest;
import com.ticketledger.dto.RefundResponse;
import com.ticketledger.exception.BusinessException;
import com.ticketledger.exception.SeatAlreadyBookedException;
import com.ticketledger.exception.ShowtimeNotFoundException;
import com.ticketledger.service.AdminAuditLogService;
import com.ticketledger.service.BookingService;
import com.ticketledger.service.IdempotencyService;
import com.ticketledger.service.gateway.PaymentGateway;
import com.ticketledger.util.CryptoUtil;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
        private final JsonMapper jsonMapper;
        private final ApplicationEventPublisher eventPublisher;

        private final TransactionTemplate transactionTemplate;
        private final PaymentGateway paymentGateway;
        private final AdminAuditLogService adminAuditLogService;

        @Override
        @Transactional(isolation = Isolation.REPEATABLE_READ)
        public void processPaymentWebhook(PaymentWebhookRequest request) {

                // 1. Lock payment (idempotency + duplicate webhook protection)
                Payment payment = paymentRepository.findByIdWithLock(request.paymentId())
                                .orElseThrow(() -> new BusinessException(
                                                "Payment not found",
                                                "PAYMENT_NOT_FOUND",
                                                HttpStatus.NOT_FOUND,
                                                Map.of("paymentId", request.paymentId())));

                // Already processed successfully → idempotent no-op
                if (payment.getStatus() == PaymentStatus.SUCCESS) {
                        return;
                }

                // 2. Lock booking (prevents race with reaper)
                Booking booking = bookingRepository.findByIdWithLock(payment.getBooking().getId())
                                .orElseThrow(() -> new BusinessException(
                                                "Booking not found",
                                                "BOOKING_NOT_FOUND",
                                                HttpStatus.NOT_FOUND,
                                                Map.of("bookingId", payment.getBooking().getId())));

                if (request.status() == PaymentStatus.SUCCESS) {

                        // 3. Persist payment success
                        payment.setStatus(PaymentStatus.SUCCESS);
                        payment.setProviderTransactionId(request.providerTransactionId());
                        payment.setProviderCapturedAt(Instant.now());
                        paymentRepository.save(payment);

                        // 4. Booking state handling
                        if (booking.getStatus() == BookingStatus.HELD) {
                                // Normal success path
                                confirmBooking(booking);

                        } else if (booking.getStatus() == BookingStatus.EXPIRED) {
                                // Late success after expiry
                                handleExpiredBookingLatePayment(booking, payment);
                        }

                } else if (request.status() == PaymentStatus.FAILED) {

                        // 5. Payment failure (do NOT cancel booking here)
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

                JsonNode requestJson = jsonMapper.valueToTree(response);
                idempotencyService.saveResponse(idempotencyKey, HttpStatus.CREATED.value(), requestJson);
                return response;
        }

        @Override
        public RefundResponse processAdminRefund(UUID bookingId, String reason, UUID adminId,
                        String idempotencyKey) {

                // Helper record to pass data out of the transaction
                record RefundContext(Payment payment, UUID logId) {
                }

                // STEP 1: Fast Transaction (Lock & Initiate)
                RefundContext context = transactionTemplate.execute(status -> {
                        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                                        .orElseThrow(() -> new BusinessException("Booking not found",
                                                        "BOOKING_NOT_FOUND", HttpStatus.NOT_FOUND));

                        if (booking.getStatus() != BookingStatus.CONFIRMED
                                        && booking.getStatus() != BookingStatus.COMPLETED) {
                                throw new BusinessException("Booking not in refundable state", "INVALID_REFUND_STATE",
                                                HttpStatus.BAD_REQUEST);
                        }

                        booking.setStatus(BookingStatus.REFUND_INITIATED);
                        bookingRepository.save(booking);

                        var log = adminAuditLogService.createRefundLog(bookingId, adminId, reason, idempotencyKey);

                        Payment payment = paymentRepository.findByBookingId(booking.getId())
                                        .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                                        .orElseThrow(() -> new BusinessException(
                                                        "No successful payment found to refund", "PAYMENT_NOT_FOUND",
                                                        HttpStatus.BAD_REQUEST));

                        return new RefundContext(payment, log.getId());
                });

                // STEP 2: Network Call (No Transaction)
                RefundResponse refundResponse;
                try {
                        refundResponse = paymentGateway.refundPayment(
                                        context.payment().getProviderTransactionId(),
                                        context.payment().getAmount(),
                                        idempotencyKey);
                } catch (Exception e) {
                        // Refund Failed at Provider -> Revert State check
                        transactionTemplate.execute(status -> {
                                Booking booking = bookingRepository.findByIdWithLock(bookingId).orElseThrow();
                                booking.setStatus(BookingStatus.CONFIRMED); // Revert to Confirmed
                                bookingRepository.save(booking);

                                adminAuditLogService.failLog(context.logId(), e.getMessage());
                                return null;
                        });
                        throw e;
                }

                // STEP 3: Finalize (Success Transaction)
                transactionTemplate.execute(status -> {
                        Booking booking = bookingRepository.findByIdWithLock(bookingId).orElseThrow();
                        booking.setStatus(BookingStatus.REFUNDED);
                        bookingRepository.save(booking);

                        // Release Seats
                        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);
                        bookingSeats.forEach(bs -> {
                                Seat seat = bs.getSeat();
                                seat.setStatus(SeatStatus.AVAILABLE);
                                seatRepository.save(seat);
                        });

                        // Update Audit Log
                        adminAuditLogService.completeLog(context.logId(), refundResponse.providerRefundId());

                        // Publish Event
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

        // Helper method needed to avoid duplication if reused, but here it's fine.

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

        private void confirmBooking(Booking booking) {
                booking.setStatus(BookingStatus.CONFIRMED);
                bookingRepository.save(booking);

                List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(booking.getId());
                bookingSeats.forEach(bs -> {
                        Seat seat = bs.getSeat();
                        seat.setStatus(SeatStatus.SOLD);
                        seatRepository.save(seat);
                });

                // 📧 EVENT 1: SUCCESS
                eventPublisher.publishEvent(new BookingConfirmedEvent(
                                booking.getId(),
                                booking.getUser().getEmail(),
                                bookingSeats.stream().map(BookingSeat::getPriceAtBooking).reduce(BigDecimal.ZERO,
                                                BigDecimal::add),
                                Instant.now()));
        }

        private void handleExpiredBookingLatePayment(Booking booking, Payment payment) {
                // Re-validate seat availability
                List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(booking.getId());
                List<UUID> seatIds = bookingSeats.stream()
                                .map(bs -> bs.getSeat().getId())
                                .sorted()
                                .toList();

                List<Seat> lockedSeats = seatRepository.lockSeats(seatIds);
                boolean allAvailable = lockedSeats.stream().allMatch(seat -> seat.getStatus() == SeatStatus.AVAILABLE);

                if (allAvailable) {
                        // Reclaim seats
                        lockedSeats.forEach(seat -> {
                                seat.setStatus(SeatStatus.SOLD);
                                seatRepository.save(seat);
                        });
                        booking.setStatus(BookingStatus.CONFIRMED);
                        bookingRepository.save(booking);

                        // 📧 EVENT 1: LATE SUCCESS
                        eventPublisher.publishEvent(new BookingConfirmedEvent(
                                        booking.getId(),
                                        booking.getUser().getEmail(),
                                        payment.getAmount(),
                                        Instant.now()));
                } else {
                        // Seats gone. Refund required.
                        booking.setStatus(BookingStatus.REFUND_REQUIRED);
                        bookingRepository.save(booking);

                        // 📧 EVENT 2: REFUND REQUIRED
                        eventPublisher.publishEvent(new BookingRefundEvent(
                                        booking.getId(),
                                        booking.getUser().getEmail(),
                                        payment.getAmount(),
                                        "Seats expired and were re-booked by another user.",
                                        Instant.now()));
                }
        }

        private String generateRequestHash(CreateBookingRequest request, UUID userId) {
                try {
                        // Combine request data with userId for hashing
                        var combined = Map.of(
                                        "userId", userId,
                                        "request", request);
                        String jsonString = jsonMapper.writeValueAsString(combined);
                        return CryptoUtil.sha256(jsonString);
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
                        return jsonMapper.treeToValue(existingKey.getResponseBody(), BookingResponse.class);
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
