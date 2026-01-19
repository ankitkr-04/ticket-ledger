package com.ticketledger.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

public class BusinessException extends TicketLedgerException {
    public BusinessException(String message, String errorCode, HttpStatus status, Map<String, Object> context) {
        super(message, errorCode, status, context);
    }

    // Convenience constructor for simple errors
    public BusinessException(String message, String errorCode, HttpStatus status) {
        super(message, errorCode, status, null);
    }
}