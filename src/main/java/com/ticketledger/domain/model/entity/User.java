package com.ticketledger.domain.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;

import com.ticketledger.domain.model.base.SoftDeletableEntity;
import com.ticketledger.domain.model.enums.UserRole;

/**
 * Represents a user account in the system.
 */
@Entity
@Table(name = "users")
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class User extends SoftDeletableEntity {

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "user_role")
    private UserRole role = UserRole.CUSTOMER;

    @Column(name = "is_verified")
    private boolean isVerified = false;
}
