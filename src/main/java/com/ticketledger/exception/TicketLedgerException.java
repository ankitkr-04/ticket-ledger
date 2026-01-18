package com.ticketledger.exception;

import java.util.Collections;
import java.util.Map;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public abstract class TicketLedgerException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;
    private final Map<String, Object> context;

    protected TicketLedgerException(String message, String errorCode, HttpStatus status, Map<String, Object> context) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.context = context != null ? context : Collections.emptyMap();
    }
}