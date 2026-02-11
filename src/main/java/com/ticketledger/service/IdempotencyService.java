package com.ticketledger.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ticketledger.config.BookingProperties;
import com.ticketledger.domain.entity.IdempotencyKey;
import com.ticketledger.domain.repository.IdempotencyKeyRepository;
import com.ticketledger.exception.BusinessException;
import com.ticketledger.exception.domain.IdempotencyConflictException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

@Service
@Slf4j
@RequiredArgsConstructor
public class IdempotencyService {
    private final IdempotencyKeyRepository repository;
    private final BookingProperties bookingProperties;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lock(UUID key, UUID userId, String requestHash) {
        var existingOpt = repository.findById(key);
        if (existingOpt.isPresent()) {
            validateHash(existingOpt.get(), requestHash);
            return false;
        }

        try {
            IdempotencyKey newKey = new IdempotencyKey();
            newKey.setId(key);
            newKey.setUserId(userId);
            newKey.setRequestHash(requestHash);
            newKey.setExpiresAt(Instant.now().plus(bookingProperties.idempotencyExpirationHours(), ChronoUnit.HOURS));
            repository.saveAndFlush(newKey);

            return true;

        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent attempt to create idempotency key {} resulted in conflict", key);
            // Refetch the existing key to validate hash
            var detectedKeyOpt = repository.findById(key).orElseThrow(
                    () -> new BusinessException("Idempotency key conflict detected but key not found after exception",
                            "IDEMPOTENCY_KEY_NOT_FOUND",
                            HttpStatus.INTERNAL_SERVER_ERROR));
            validateHash(detectedKeyOpt, requestHash);
            return false;

        }
    }

    public Optional<IdempotencyKey> findKey(UUID key) {
        return repository.findById(key);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveResponse(UUID key, int status, JsonNode body) {
        var existingKeyOpt = repository.findById(key)
                .orElseThrow(() -> new BusinessException("Idempotency key not found when saving response",
                        "IDEMPOTENCY_KEY_NOT_FOUND",
                        HttpStatus.INTERNAL_SERVER_ERROR));

        existingKeyOpt.setResponseStatus(status);
        existingKeyOpt.setResponseBody(body);
        repository.save(existingKeyOpt);
    }

    private void validateHash(IdempotencyKey storedKey, String incomingHash) {
        if (storedKey.getRequestHash() != null && !storedKey.getRequestHash().equals(incomingHash)) {

            log.warn("Idempotency key conflict: incoming hash {} does not match stored hash {}", incomingHash,
                    storedKey.getRequestHash());
            throw new IdempotencyConflictException(incomingHash, storedKey.getRequestHash());
        }
    }
}
