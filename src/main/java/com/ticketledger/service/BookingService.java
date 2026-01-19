package com.ticketledger.service;

import java.util.UUID;

import com.ticketledger.dto.BookingResponse;
import com.ticketledger.dto.CreateBookingRequest;

public interface BookingService {
    BookingResponse createBooking(CreateBookingRequest request, UUID userId);
}
