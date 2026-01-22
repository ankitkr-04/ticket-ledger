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
import com.ticketledger.service.BookingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController()
@RequestMapping(name = RouteConstant.BOOKING_PATH, version = RouteConstant.API_VERSION_V1)
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(name = HttpHeaderConstant.IDEMPOTENCY_KEY, required = true) UUID idempotencyKey,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        BookingResponse booking = bookingService.createBooking(request, currentUser.getId(), idempotencyKey);

        String requestId = UUID.randomUUID().toString();

        return new ResponseEntity<>(
                ApiResponse.success(booking, requestId),
                HttpStatus.CREATED);
    }
}