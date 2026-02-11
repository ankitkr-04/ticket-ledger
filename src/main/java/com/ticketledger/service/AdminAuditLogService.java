package com.ticketledger.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ticketledger.config.AdminProperties;
import com.ticketledger.domain.entity.AdminAuditLog;
import com.ticketledger.domain.entity.Booking;
import com.ticketledger.domain.entity.Showtime;
import com.ticketledger.domain.entity.User;
import com.ticketledger.domain.enums.AdminLogAction;
import com.ticketledger.domain.enums.AdminLogStatus;
import com.ticketledger.domain.enums.PaymentProvider;
import com.ticketledger.domain.repository.AdminAuditLogRepository;
import com.ticketledger.domain.repository.BookingRepository;
import com.ticketledger.domain.repository.ShowtimeRepository;
import com.ticketledger.domain.repository.TheaterRepository;
import com.ticketledger.domain.repository.UserRepository;
import com.ticketledger.exception.common.NotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuditLogService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ShowtimeRepository showtimeRepository;
    private final TheaterRepository theaterRepository;
    private final AdminProperties adminProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(UUID adminId, UUID bookingId, AdminLogAction action, AdminLogStatus status, String reason) {
        // 1. Resolve Actor (Default to System constant if null)
        UUID actorId = (adminId != null) ? adminId : adminProperties.systemUserId();
        User admin = userRepository.getReferenceById(actorId);

        // 2. Resolve Target Booking
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found", Map.of("bookingId", bookingId)));

        // 3. Create Entry
        AdminAuditLog logEntry = new AdminAuditLog();
        logEntry.setBooking(booking);

        // Resolve theater id via query to avoid lazy graph traversal in async contexts.
        UUID theaterId = bookingRepository.findTheaterIdById(bookingId)
                .orElseThrow(() -> new NotFoundException("Theater not found for booking", Map.of("bookingId", bookingId)));
        logEntry.setTheater(theaterRepository.getReferenceById(theaterId));

        logEntry.setAdminUser(admin);
        logEntry.setAction(action);
        logEntry.setStatus(status);
        logEntry.setReason(reason);

        if (status == AdminLogStatus.COMPLETED) {
            logEntry.setCompletedAt(Instant.now());
        }

        adminAuditLogRepository.save(logEntry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AdminAuditLog createRefundLog(UUID bookingId, UUID adminId, String reason, String idempotencyKey) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found", Map.of("bookingId", bookingId)));

        User admin = userRepository.getReferenceById(adminId); // Optimized: no fetch needed if ID valid

        AdminAuditLog logEntry = new AdminAuditLog();
        logEntry.setBooking(booking);
        // We resolve theater from booking to support queries.
        // Note: admin access was already checked via AdminAuthorizationService
        logEntry.setTheater(booking.getShowtime().getScreen().getTheater());
        logEntry.setAdminUser(admin);
        logEntry.setAction(AdminLogAction.REFUND);
        logEntry.setStatus(AdminLogStatus.INITIATED);
        logEntry.setReason(reason);
        logEntry.setIdempotencyKey(idempotencyKey);
        logEntry.setProvider(PaymentProvider.STRIPE);

        return adminAuditLogRepository.save(logEntry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeLog(UUID logId, String providerRefundId) {
        AdminAuditLog logEntry = adminAuditLogRepository.findById(logId)
                .orElseThrow(() -> new NotFoundException("Audit log not found", Map.of("logId", logId)));

        logEntry.setStatus(AdminLogStatus.COMPLETED);
        logEntry.setProviderRefundId(providerRefundId);
        logEntry.setCompletedAt(Instant.now());

        adminAuditLogRepository.save(logEntry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failLog(UUID logId, String errorReason) {
        AdminAuditLog logEntry = adminAuditLogRepository.findById(logId)
                .orElseThrow(() -> new NotFoundException("Audit log not found", Map.of("logId", logId)));

        logEntry.setStatus(AdminLogStatus.FAILED);
        logEntry.setReason(logEntry.getReason() + " | ERROR: " + errorReason);

        adminAuditLogRepository.save(logEntry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AdminAuditLog createShowtimeLog(UUID showtimeId, UUID adminId, AdminLogAction action, String reason,
            String idempotencyKey) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new NotFoundException("Showtime not found", Map.of("showtimeId", showtimeId)));

        User admin = userRepository.getReferenceById(adminId);

        AdminAuditLog logEntry = new AdminAuditLog();
        logEntry.setShowtime(showtime);
        logEntry.setTheater(showtime.getScreen().getTheater());
        logEntry.setAdminUser(admin);
        logEntry.setAction(action);
        // Showtime pause/resume is synchronous; log should be finalized immediately.
        logEntry.setStatus(AdminLogStatus.COMPLETED);
        logEntry.setCompletedAt(Instant.now());
        logEntry.setReason(reason);
        logEntry.setIdempotencyKey(idempotencyKey);

        return adminAuditLogRepository.save(logEntry);
    }

    public void logSystemAction(UUID bookingId, AdminLogAction action, AdminLogStatus status, String reason) {
        logAction(adminProperties.systemUserId(), bookingId, action, status, reason);
    }
}
