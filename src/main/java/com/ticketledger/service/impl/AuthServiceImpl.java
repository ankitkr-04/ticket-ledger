package com.ticketledger.service.impl;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketledger.config.JwtProperties;
import com.ticketledger.domain.model.entity.RefreshToken;
import com.ticketledger.domain.model.entity.User;
import com.ticketledger.domain.model.enums.UserRole;
import com.ticketledger.domain.repository.RefreshTokenRepository;
import com.ticketledger.domain.repository.UserRepository;
import com.ticketledger.dto.AuthResponse;
import com.ticketledger.dto.LoginRequest;
import com.ticketledger.dto.RefreshTokenRequest;
import com.ticketledger.dto.RegisterRequest;
import com.ticketledger.exception.BusinessException;
import com.ticketledger.security.JwtService;
import com.ticketledger.service.AuthService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    // Secure random for opaque tokens (better than UUID)
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // 1. Authenticate (Checks password)
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        // 2. Fetch Entity
        var user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // 3. Generate Tokens
        var accessToken = jwtService.generateToken(user.getEmail()); // Now works with String overload
        var refreshToken = createRefreshToken(user);

        // 4. Return Response (using the 3-arg constructor from DTO step)
        return new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                jwtProperties.accessTokenExpiration());
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 1. Check if email already exists
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(
                    "Email already in use",
                    "EMAIL_ALREADY_EXISTS",
                    HttpStatus.CONFLICT);
        }

        // 2. Hash password
        String hashedPassword = passwordEncoder.encode(request.password());

        // 3. Create user entity
        var user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(hashedPassword);
        user.setRole(UserRole.CUSTOMER);
        user.setVerified(true); // For MVP - skip email verification

        // 4. Save user
        user = userRepository.save(user);

        // 5. Auto-login: Generate tokens
        var accessToken = jwtService.generateToken(user.getEmail());
        var refreshToken = createRefreshToken(user);

        // 6. Return authentication response
        return new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                jwtProperties.accessTokenExpiration());
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String incomingToken = request.refreshToken();

        // 1. Find token in DB
        var refreshToken = refreshTokenRepository.findByToken(incomingToken)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));

        // 2. Security: Verify if revoked
        if (refreshToken.isRevoked()) {
            revokeAllUserTokens(refreshToken.getUser());
            throw new RuntimeException("Security Alert: Reuse of revoked token detected");
        }

        // 3. Verify expiry
        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new RuntimeException("Refresh token expired");
        }

        // 4. Token Rotation: Revoke old, issue new
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        var user = refreshToken.getUser();
        var newAccessToken = jwtService.generateToken(user.getEmail());
        var newRefreshToken = createRefreshToken(user);

        return new AuthResponse(
                newAccessToken,
                newRefreshToken.getToken(),
                jwtProperties.accessTokenExpiration());
    }

    private RefreshToken createRefreshToken(User user) {
        // Generate secure opaque string
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String tokenString = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        var token = new RefreshToken();
        token.setUser(user);
        token.setToken(tokenString);
        token.setExpiresAt(Instant.now().plusMillis(jwtProperties.refreshTokenExpiration()));
        token.setRevoked(false);

        return refreshTokenRepository.save(token);
    }

    @Override
    @Transactional
    public void logout(String email) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        revokeAllUserTokens(user);
    }

    private void revokeAllUserTokens(User user) {
        var validTokens = refreshTokenRepository.findAllValidTokensByUser(user.getId());
        if (validTokens.isEmpty())
            return;
        validTokens.forEach(t -> t.setRevoked(true));
        refreshTokenRepository.saveAll(validTokens);
    }
}
