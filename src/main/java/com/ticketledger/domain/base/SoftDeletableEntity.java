package com.ticketledger.domain.base;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Base entity for entities supporting soft delete.
 * Extends BaseEntity with deletedAt field and SQL annotations.
 */
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
public abstract class SoftDeletableEntity extends BaseEntity {

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
