package com.ticketledger.domain.model.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.ticketledger.domain.model.key.BookingSeatId;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Junction table linking bookings to seats with price snapshot.
 * Uses @EmbeddedId + @MapsId pattern for clean composite key handling.
 */
@Entity
@Table(name = "booking_seats")
@Getter
@Setter
@NoArgsConstructor
public class BookingSeat {

    @EmbeddedId
    private BookingSeatId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("bookingId") // Maps "bookingId" attribute in BookingSeatId
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("seatId") // Maps "seatId" attribute in BookingSeatId
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @Column(name = "price_at_booking", precision = 10, scale = 2)
    private BigDecimal priceAtBooking;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public BookingSeat(Booking booking, Seat seat, BigDecimal priceAtBooking) {
        this.id = new BookingSeatId(booking.getId(), seat.getId());
        this.booking = booking;
        this.seat = seat;
        this.priceAtBooking = priceAtBooking;
    }
}