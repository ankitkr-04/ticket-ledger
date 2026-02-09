package com.ticketledger.exception;

import org.springframework.http.HttpStatus;

/**
 * Signals a non-retryable payment gateway error.
 */
public class PermanentGatewayException extends BusinessException {

    public PermanentGatewayException(String message, String errorCode, HttpStatus status) {
        super(message, errorCode, status);
    }
}
