package com.ticketledger.service;

import java.util.Optional;
import java.util.UUID;

import com.ticketledger.domain.entity.IdempotencyKey;
import com.ticketledger.exception.IdempotencyConflictException;

import tools.jackson.databind.JsonNode;

public interface IdempotencyService {
    /**
     * Attempts to acquire a lock for the given key, userId, and requestHash.
     * 
     * @param key
     * @param userId
     * @param requestHash
     * @return true if the lock was successfully acquired, false otherwise.
     * @throws IdempotencyConflictException if there is a conflict with an existing
     *                                      lock.
     */
    boolean lock(UUID key, UUID userId, String requestHash);

    /**
     * Releases the lock for the given key.
     * 
     * @param key
     */
    Optional<IdempotencyKey> findKey(UUID key);

    /**
     * Saves the response for the given key.
     * 
     * @param key
     * @param status
     * @param body
     */
    void saveResponse(UUID key, int status, JsonNode body);

}
