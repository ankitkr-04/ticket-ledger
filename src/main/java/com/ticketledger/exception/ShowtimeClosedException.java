package com.ticketledger.exception;


import java.util.Map;

import org.springframework.http.HttpStatus;

import com.ticketledger.domain.model.enums.ShowtimeStatus;

public class ShowtimeClosedException extends TicketLedgerException {
    public ShowtimeClosedException(ShowtimeStatus status) {
        super(
            "Showtime is not active", 
            "SHOWTIME_CLOSED", 
            HttpStatus.BAD_REQUEST, 
            Map.of("currentStatus", status)
        );
    }
}