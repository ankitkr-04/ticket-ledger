package com.ticketledger.domain.entity;

import com.ticketledger.domain.base.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Many-to-many relationship between admins and theaters they can manage.
 * Represents theater-scoped access control for admin operations.
 */
@Entity
@Table(name = "admin_theater_access", uniqueConstraints = {
        @UniqueConstraint(name = "uq_admin_theater", columnNames = { "user_id", "theater_id" })
})
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

    /**
     * Factory method to create access grant
     */
    public static AdminTheaterAccess grant(User admin, Theater theater) {
        return AdminTheaterAccess.builder()
                .user(admin)
                .theater(theater)
                .build();
    }
}
