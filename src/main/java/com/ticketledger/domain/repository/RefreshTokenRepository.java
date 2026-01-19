package com.ticketledger.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ticketledger.domain.model.entity.RefreshToken;
import com.ticketledger.domain.model.entity.User;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    @Query("""
            SELECT rt FROM RefreshToken rt
            WHERE rt.user.id = :userId AND rt.revoked = false AND rt.expiresAt > CURRENT_TIMESTAMP
                """)
    List<RefreshToken> findAllValidTokensByUser(UUID userId);

    @Modifying
    void deleteByUser(User user);

}
