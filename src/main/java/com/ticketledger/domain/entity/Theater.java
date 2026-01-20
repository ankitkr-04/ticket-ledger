package com.ticketledger.domain.entity;

import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.ticketledger.domain.base.SoftDeletableEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a physical theater/multiplex building.
 * This is the ownership boundary for admin operations.
 */
@Entity
@Table(name = "theaters")
@SQLDelete(sql = "UPDATE theaters SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class Theater extends SoftDeletableEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(columnDefinition = "TEXT")
    private String address;

    @OneToMany(mappedBy = "theater", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Screen> screens = new HashSet<>();

    @OneToMany(mappedBy = "theater", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<AdminTheaterAccess> adminAccess = new HashSet<>();
}
