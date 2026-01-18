package com.ticketledger.exception;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;

public class SeatAlreadyBookedException extends TicketLedgerException {
    public SeatAlreadyBookedException(List<UUID> seatIds) {
        super("Selected seats are already booked", "SEAT_ALREADY_BOOKED", HttpStatus.CONFLICT,
                Map.of("rejectedSeatIds", seatIds));
    }
}