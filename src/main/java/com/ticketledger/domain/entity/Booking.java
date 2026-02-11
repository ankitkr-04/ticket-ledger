package com.ticketledger.domain.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.ticketledger.domain.base.BaseEntity;
import com.ticketledger.domain.enums.BookingStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

    @Column(name = "displaced_by_booking_id")
    private UUID displacedByBookingId;

    @Column(name = "system_cancellation_reason")
    private String systemCancellationReason;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    /**
     * Transitions the booking to a new status, validating against the state machine.
     *
     * @param newStatus the target status
     * @throws IllegalStateException if the transition is not allowed
     */
    public void transitionTo(BookingStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    "Invalid booking status transition: " + this.status + " → " + newStatus);
        }
        this.status = newStatus;
    }
}
