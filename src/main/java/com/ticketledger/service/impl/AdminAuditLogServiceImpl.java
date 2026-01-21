package com.ticketledger.service.impl;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
import com.ticketledger.domain.repository.UserRepository;
import com.ticketledger.exception.NotFoundException;
import com.ticketledger.service.AdminAuditLogService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuditLogServiceImpl implements AdminAuditLogService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ShowtimeRepository showtimeRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AdminAuditLog createRefundLog(UUID bookingId, UUID adminId, String reason, String idempotencyKey) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));

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

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeLog(UUID logId, String providerRefundId) {
        AdminAuditLog logEntry = adminAuditLogRepository.findById(logId)
                .orElseThrow(() -> new NotFoundException("Audit log not found: " + logId));

        logEntry.setStatus(AdminLogStatus.COMPLETED);
        logEntry.setProviderRefundId(providerRefundId);
        logEntry.setCompletedAt(Instant.now());

        adminAuditLogRepository.save(logEntry);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failLog(UUID logId, String errorReason) {
        AdminAuditLog logEntry = adminAuditLogRepository.findById(logId)
                .orElseThrow(() -> new NotFoundException("Audit log not found: " + logId));

        logEntry.setStatus(AdminLogStatus.FAILED);
        logEntry.setReason(logEntry.getReason() + " | ERROR: " + errorReason);

        adminAuditLogRepository.save(logEntry);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AdminAuditLog createShowtimeLog(UUID showtimeId, UUID adminId, AdminLogAction action, String reason,
            String idempotencyKey) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new NotFoundException("Showtime not found: " + showtimeId));

        User admin = userRepository.getReferenceById(adminId);

        AdminAuditLog logEntry = new AdminAuditLog();
        logEntry.setShowtime(showtime);
        logEntry.setTheater(showtime.getScreen().getTheater());
        logEntry.setAdminUser(admin);
        logEntry.setAction(action);
        logEntry.setStatus(AdminLogStatus.INITIATED);
        logEntry.setReason(reason);
        logEntry.setIdempotencyKey(idempotencyKey);

        return adminAuditLogRepository.save(logEntry);
    }
}
