package com.ticketledger.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

public class TheaterAccessDeniedException extends TicketLedgerException {

    public TheaterAccessDeniedException(String message) {
        super(message, "THEATER_ACCESS_DENIED", HttpStatus.FORBIDDEN, null);
    }

    public TheaterAccessDeniedException(String message, Map<String, Object> context) {
        super(message, "THEATER_ACCESS_DENIED", HttpStatus.FORBIDDEN, context);
    }
}
