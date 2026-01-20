package com.ticketledger.domain.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.ticketledger.domain.base.SoftDeletableEntity;
import com.ticketledger.domain.enums.UserRole;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private UserRole role = UserRole.CUSTOMER;

    @Column(name = "is_verified")
    private boolean isVerified = false;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "profile_image_url", columnDefinition = "TEXT")
    private String profileImageUrl;
}
