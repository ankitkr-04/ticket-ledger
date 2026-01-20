package com.ticketledger.domain.key;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * Embeddable composite key for the BookingSeat junction table.
 * <p>
 * FIX: Replaced @Data with @Getter, @Setter, and @EqualsAndHashCode.
 * JPA keys must have consistent hash codes; @Data includes all fields
 * which is fine for UUIDs, but explicit control is safer for ORM keys.
 */
@Embeddable
@Getter
@Setter
@EqualsAndHashCode // Ensures correct behavior in Sets and Hibernate Context
@NoArgsConstructor
@AllArgsConstructor
public class BookingSeatId implements Serializable {

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "seat_id")
    private UUID seatId;
}