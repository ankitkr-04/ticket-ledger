package com.ticketledger.domain.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;

import com.ticketledger.domain.model.base.SoftDeletableEntity;

/**
 * Represents a movie available for scheduling.
 */
@Entity
@Table(name = "movies")
@SQLDelete(sql = "UPDATE movies SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class Movie extends SoftDeletableEntity {

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
}
