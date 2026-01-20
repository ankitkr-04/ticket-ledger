package com.ticketledger.domain.entity;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.ticketledger.domain.base.SoftDeletableEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a physical screening room in the theater.
 */
@Entity
@Table(name = "screens")
@SQLDelete(sql = "UPDATE screens SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class Screen extends SoftDeletableEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "total_seats")
    private int totalSeats = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;
}
