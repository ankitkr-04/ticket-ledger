package com.ticketledger.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBookingRequest(

                @NotNull(message = "Showtime ID is required") UUID showtimeId,

                @NotEmpty(message = "At least one seat must be selected") @Size(min = 1, max = 10, message = "Cannot book more than 10 seats at once") List<@NotNull UUID> seatIds) {
}