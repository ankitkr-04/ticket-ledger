package com.ticketledger.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

public class NotFoundException extends TicketLedgerException {

    public NotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, null);
    }

    public NotFoundException(String message, Map<String, Object> context) {
        super(message, "RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, context);
    }
}
