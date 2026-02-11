package com.ticketledger.exception;

import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public abstract class ApplicationException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;
    private final Map<String, Object> context;

    protected ApplicationException(String message, String errorCode, HttpStatus status, Map<String, Object> context) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.context = Objects.requireNonNullElse(context, Map.of());
    }
}
