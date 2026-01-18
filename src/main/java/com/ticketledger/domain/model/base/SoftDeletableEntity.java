package com.ticketledger.domain.model.base;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;

import java.time.*;

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
