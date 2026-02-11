package com.ticketledger.exception.domain;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.ticketledger.constant.ErrorCodeConstant;
import com.ticketledger.exception.ApplicationException;

public class ShowtimeExpiredException extends ApplicationException {
    public ShowtimeExpiredException(Instant startTime) {
        super(
                "Showtime has already started",
                ErrorCodeConstant.SHOWTIME_STARTED,
                HttpStatus.BAD_REQUEST,
                Map.of("startTime", startTime));
    }
}
