package com.ticketledger.exception;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;

public class ShowtimeExpiredException extends TicketLedgerException {
    public ShowtimeExpiredException(Instant startTime) {
        super(
                "Showtime has already started",
                "SHOWTIME_STARTED",
                HttpStatus.BAD_REQUEST,
                Map.of("startTime", startTime));
    }
}