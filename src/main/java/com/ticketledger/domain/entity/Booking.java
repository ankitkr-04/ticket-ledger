package com.ticketledger.domain.entity;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.ticketledger.domain.base.BaseEntity;
import com.ticketledger.domain.enums.BookingStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a booking reservation in the system.
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "booking_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private BookingStatus status = BookingStatus.HELD;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
