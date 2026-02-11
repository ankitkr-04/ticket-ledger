package com.ticketledger.exception.domain;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.ticketledger.constant.ErrorCodeConstant;
import com.ticketledger.exception.ApplicationException;

public class ShowtimeNotFoundException extends ApplicationException {
    public ShowtimeNotFoundException(UUID showtimeId) {
        super("Showtime not found", ErrorCodeConstant.SHOWTIME_NOT_FOUND, HttpStatus.NOT_FOUND,
                Map.of("showtimeId", showtimeId));
    }
}
