package com.ticketledger.domain.base;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Base entity for entities supporting soft delete.
 * Extends BaseEntity with deletedAt field and version for optimistic locking.
 */
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
public abstract class SoftDeletableEntity extends BaseEntity {

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
