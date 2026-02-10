package com.ticketledger.api.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketledger.constant.RouteConstant;
import com.ticketledger.dto.AdminRefundRequest;
import com.ticketledger.dto.ApiResponse;
import com.ticketledger.dto.RefundResponse;
import com.ticketledger.service.AdminAuthorizationService;
import com.ticketledger.service.booking.BookingService;
import com.ticketledger.service.context.RequestContext;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(RouteConstant.ADMIN_BOOKING_PATH)
@RequiredArgsConstructor
@Slf4j
public class AdminBookingController {

    private final BookingService bookingService;
    private final AdminAuthorizationService adminAuthorizationService;
    private final RequestContext requestContext;

    /**
     * Process a manual refund for a booking.
     * <p>
     * Enforces:
     * 1. ADMIN role check
     * 2. Theater-scope access check (assertion)
     * 3. Idempotency via Idempotency-Key header
     *
     * @param bookingId      The ID of the booking to refund
     * @param request        The refund request containing the reason
     * @param idempotencyKey critical header for preventing double refunds
     * @return The refund details
     */
    @PostMapping("/{bookingId}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RefundResponse>> refundBooking(
            @PathVariable UUID bookingId,
            @Valid @RequestBody AdminRefundRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        log.info("Admin initiated refund for booking: {}", bookingId);

        // 1. Gatekeeper & Identity combined
        UUID adminId = adminAuthorizationService.assertBookingAccess(bookingId);

        // 2. Process Refund
        RefundResponse response = bookingService.processAdminRefund(
                bookingId,
                request.reason(),
                adminId,
                idempotencyKey);

        return ResponseEntity.ok(ApiResponse.success(response, requestContext.getRequestId()));
    }
}
