package com.ticketledger.domain.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;

import java.time.*;

import com.ticketledger.domain.model.base.SoftDeletableEntity;
import com.ticketledger.domain.model.enums.ShowtimeStatus;

/**
 * Represents a scheduled screening of a movie on a screen.
 */
@Entity
@Table(name = "showtimes")
@SQLDelete(sql = "UPDATE showtimes SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class Showtime extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", insertable = false, updatable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "showtime_status")
    private ShowtimeStatus status = ShowtimeStatus.ACTIVE;
}
