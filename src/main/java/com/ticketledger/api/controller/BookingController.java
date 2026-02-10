package com.ticketledger.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.ticketledger.constant.HttpHeaderConstant;
import com.ticketledger.constant.RouteConstant;
import com.ticketledger.dto.ApiResponse;
import com.ticketledger.dto.BookingResponse;
import com.ticketledger.dto.CreateBookingRequest;
import com.ticketledger.security.AuthenticatedUser;
import com.ticketledger.service.booking.BookingService;
import com.ticketledger.service.context.RequestContext;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(RouteConstant.BOOKING_PATH)
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final RequestContext requestContext;

    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(name = HttpHeaderConstant.IDEMPOTENCY_KEY, required = true) UUID idempotencyKey,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        BookingResponse booking = bookingService.createBooking(request, currentUser.getId(), idempotencyKey);

        return new ResponseEntity<>(
                ApiResponse.success(booking, requestContext.getRequestId()),
                HttpStatus.CREATED);
    }
}
