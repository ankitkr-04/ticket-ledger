package com.ticketledger.domain.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.ticketledger.domain.base.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Many-to-many relationship between admins and theaters they can manage.
 * Represents theater-scoped access control for admin operations.
 */
@Entity
@Table(name = "admin_theater_access")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminTheaterAccess extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;

    @CreationTimestamp
    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isActive() {
        return revokedAt == null;
    }
}
