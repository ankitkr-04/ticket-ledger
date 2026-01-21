package com.ticketledger.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.ticketledger.domain.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
public class SeatTier extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "price_multiplier", precision = 3, scale = 2)
    private BigDecimal priceMultiplier = BigDecimal.ONE;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
