package com.ticketledger.domain.entity;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.ticketledger.domain.base.SoftDeletableEntity;
import com.ticketledger.domain.enums.ShowtimeStatus;
import com.ticketledger.exception.domain.ShowtimeClosedException;
import com.ticketledger.exception.domain.ShowtimeExpiredException;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "showtime_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ShowtimeStatus status = ShowtimeStatus.ACTIVE;

    public void checkBookable() {
        if (this.status != ShowtimeStatus.ACTIVE) {
            throw new ShowtimeClosedException(this.status);
        }

        if (this.startTime.isBefore(Instant.now())) {
            throw new ShowtimeExpiredException(this.startTime);
        }
    }
}
