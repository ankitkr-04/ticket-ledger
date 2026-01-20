package com.ticketledger.domain.entity;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.ticketledger.domain.base.SoftDeletableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(length = 100)
    private String genre;

    @Column(length = 50)
    private String language;
}
