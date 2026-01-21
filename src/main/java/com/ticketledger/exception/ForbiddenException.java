package com.ticketledger.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends TicketLedgerException {

    public ForbiddenException(String message) {
        super(message, "ACCESS_DENIED", HttpStatus.FORBIDDEN, null);
    }

    public ForbiddenException(String message, Map<String, Object> context) {
        super(message, "ACCESS_DENIED", HttpStatus.FORBIDDEN, context);
    }
}
