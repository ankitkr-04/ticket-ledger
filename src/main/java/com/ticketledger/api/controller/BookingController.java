package com.ticketledger.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketledger.constant.RouteConstant;
import com.ticketledger.dto.ApiResponse;
import com.ticketledger.dto.BookingResponse;
import com.ticketledger.dto.CreateBookingRequest;
import com.ticketledger.service.BookingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(RouteConstant.BOOKING_PATH)
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    // TODO: Remove mock when Security is implemented
    // This ID *MUST* exist in your database (See Step 2 below)
    private static final UUID MOCK_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @Valid @RequestBody CreateBookingRequest request) {

        // 1. Call the Business Logic
        BookingResponse booking = bookingService.createBooking(request, MOCK_USER_ID);

        // 2. Generate Request ID (Mock for now)
        String requestId = UUID.randomUUID().toString();

        // 3. Return Standard Envelope
        return new ResponseEntity<>(
                ApiResponse.success(booking, requestId),
                HttpStatus.CREATED);
    }
}