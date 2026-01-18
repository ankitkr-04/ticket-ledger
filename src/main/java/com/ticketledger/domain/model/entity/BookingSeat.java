package com.ticketledger.domain.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;

import java.math.*;
import java.time.*;
import java.util.*;

import com.ticketledger.domain.model.key.BookingSeatId;

/**
 * Junction table linking bookings to seats with price snapshot.
 */
@Entity
@Table(name = "booking_seats")
@IdClass(BookingSeatId.class)
@Getter
@Setter
@NoArgsConstructor
public class BookingSeat {

    @Id
    @Column(name = "booking_id")
    private UUID bookingId;

    @Id
    @Column(name = "seat_id")
    private UUID seatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", insertable = false, updatable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", insertable = false, updatable = false)
    private Seat seat;

    @Column(name = "price_at_booking", precision = 10, scale = 2)
    private BigDecimal priceAtBooking;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
