package com.ticketledger.domain.model.key;

import jakarta.persistence.*;
import lombok.*;

import java.io.*;
import java.util.*;

/**
 * Embeddable composite key for the BookingSeat junction table.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingSeatId implements Serializable {

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "seat_id")
    private UUID seatId;
}
