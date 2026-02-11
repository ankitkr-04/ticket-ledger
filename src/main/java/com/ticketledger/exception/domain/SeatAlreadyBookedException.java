package com.ticketledger.exception.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.ticketledger.constant.ErrorCodeConstant;
import com.ticketledger.exception.ApplicationException;

public class SeatAlreadyBookedException extends ApplicationException {
    public SeatAlreadyBookedException(List<UUID> seatIds) {
        super("Selected seats are already booked", ErrorCodeConstant.SEAT_ALREADY_BOOKED, HttpStatus.CONFLICT,
                Map.of("rejectedSeatIds", seatIds));
    }
}
