package com.ticketledger.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketledger.constant.ErrorCodeConstant;
import com.ticketledger.constant.ErrorMessageConstant;
import com.ticketledger.constant.HttpHeaderConstant;
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

    @PatchMapping("/{showtimeId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ShowtimePauseResponse>> updateShowtimeStatus(
            @PathVariable UUID showtimeId,
            @Valid @RequestBody UpdateShowtimeStatusRequest request,
            @RequestHeader(HttpHeaderConstant.IDEMPOTENCY_KEY) String idempotencyKey) {

        validateStatusRequest(request.status());
        UUID adminId = adminAuthorizationService.assertShowtimeAccess(showtimeId);

        ShowtimePauseResponse response = showtimeService.pauseShowtime(
                showtimeId,
                request.reason(),
                adminId,
                idempotencyKey);

        return ResponseEntity.ok(ApiResponse.success(response, requestContext.getRequestId()));
    }

    private static void validateStatusRequest(ShowtimeStatus requestedStatus) {
        if (requestedStatus != ShowtimeStatus.PAUSED) {
            throw new BusinessException(
                    ErrorMessageConstant.ONLY_PAUSED_STATUS_SUPPORTED,
                    ErrorCodeConstant.INVALID_STATUS_TRANSITION,
                    HttpStatus.BAD_REQUEST);
        }
    }
}
