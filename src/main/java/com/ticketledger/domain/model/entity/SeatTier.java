package com.ticketledger.domain.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;

import java.math.*;

import com.ticketledger.domain.model.base.SoftDeletableEntity;

/**
 * Represents a pricing tier for seats (e.g., VIP, Regular, Balcony).
 */
@Entity
@Table(name = "seat_tiers")
@SQLDelete(sql = "UPDATE seat_tiers SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class SeatTier extends SoftDeletableEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "price_multiplier", precision = 3, scale = 2)
    private BigDecimal priceMultiplier = BigDecimal.ONE;
}
