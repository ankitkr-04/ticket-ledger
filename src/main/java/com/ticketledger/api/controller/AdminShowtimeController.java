package com.ticketledger.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.ticketledger.constant.RouteConstant;
import com.ticketledger.domain.enums.ShowtimeStatus;
import com.ticketledger.dto.ApiResponse;
import com.ticketledger.dto.ShowtimePauseResponse;
import com.ticketledger.dto.UpdateShowtimeStatusRequest;
import com.ticketledger.exception.BusinessException;
import com.ticketledger.service.AdminAuthorizationService;
import com.ticketledger.service.ShowtimeService;
import com.ticketledger.service.context.RequestContext;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(RouteConstant.ADMIN_SHOWTIME_PATH)
@RequiredArgsConstructor
@Slf4j
public class AdminShowtimeController {

    private final ShowtimeService showtimeService;
    private final AdminAuthorizationService adminAuthorizationService;
    private final RequestContext requestContext;

    /**
     * Pause a showtime and expire all held bookings.
     * <p>
     * Enforces:
     * 1. ADMIN role check
     * 2. Theater-scope access check
     * 3. Idempotency
     *
     * @param showtimeId     ID of the showtime
     * @param request        Target status (must be PAUSED)
     * @param idempotencyKey for audit
     * @return Operation stats
     */
    @PatchMapping("/{showtimeId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ShowtimePauseResponse>> updateShowtimeStatus(
            @PathVariable UUID showtimeId,
            @Valid @RequestBody UpdateShowtimeStatusRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        // Validation: Only allow PAUSED for now
        if (request.status() != ShowtimeStatus.PAUSED) {
            throw new BusinessException("Only PAUSED status is currently supported via this endpoint",
                    "INVALID_STATUS_TRANSITION", HttpStatus.BAD_REQUEST);
        }

        // 1. Gatekeeper & Identity combined
        UUID adminId = adminAuthorizationService.assertShowtimeAccess(showtimeId);

        // 2. Service Call
        ShowtimePauseResponse response = showtimeService.pauseShowtime(
                showtimeId,
                request.reason(),
                adminId,
                idempotencyKey);

        return ResponseEntity.ok(ApiResponse.success(response, requestContext.getRequestId()));
    }
}
