package com.ticketledger.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ticketledger.domain.model.entity.IdempotencyKey;

@Repository
public interface IdempotencyKeyRepository
        extends JpaRepository<IdempotencyKey, UUID> {

}
