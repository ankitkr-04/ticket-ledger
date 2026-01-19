package com.ticketledger.domain.model.entity;

import java.time.Instant;
import java.util.UUID;

import com.ticketledger.domain.model.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
public class IdempotencyKey extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    /**
     * Stored as JSONB in Postgres.
     * Serialized/deserialized at the application boundary.
     */
    @Column(name = "response_body", columnDefinition = "jsonb")
    private String responseBody;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
