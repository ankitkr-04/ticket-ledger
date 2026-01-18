package com.ticketledger.domain.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;

import com.ticketledger.domain.model.base.SoftDeletableEntity;

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
}
