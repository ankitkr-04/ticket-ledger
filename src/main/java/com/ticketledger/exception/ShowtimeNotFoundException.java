package com.ticketledger.exception;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;

public class ShowtimeNotFoundException extends TicketLedgerException {
    public ShowtimeNotFoundException(UUID showtimeId) {
        super("Showtime not found", "SHOWTIME_NOT_FOUND", HttpStatus.NOT_FOUND,Map.of("showtimeId", showtimeId));
    }
}