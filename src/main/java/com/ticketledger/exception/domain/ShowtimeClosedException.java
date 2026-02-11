package com.ticketledger.exception.domain;

import java.util.Map;

import org.springframework.http.HttpStatus;

import com.ticketledger.constant.ErrorCodeConstant;
import com.ticketledger.domain.enums.ShowtimeStatus;
import com.ticketledger.exception.ApplicationException;

public class ShowtimeClosedException extends ApplicationException {
    public ShowtimeClosedException(ShowtimeStatus status) {
        super(
                "Showtime is not active",
                ErrorCodeConstant.SHOWTIME_CLOSED,
                HttpStatus.BAD_REQUEST,
                Map.of("currentStatus", status));
    }
}
