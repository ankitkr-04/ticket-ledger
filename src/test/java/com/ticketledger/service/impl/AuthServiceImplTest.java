package com.ticketledger.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ticketledger.config.JwtProperties;
import com.ticketledger.domain.entity.RefreshToken;
import com.ticketledger.domain.entity.User;
import com.ticketledger.domain.enums.UserRole;
import com.ticketledger.domain.repository.RefreshTokenRepository;
import com.ticketledger.domain.repository.UserRepository;
import com.ticketledger.dto.AuthResponse;
import com.ticketledger.dto.LoginRequest;
import com.ticketledger.dto.RefreshTokenRequest;
import com.ticketledger.dto.RegisterRequest;
import com.ticketledger.exception.BusinessException;
import com.ticketledger.security.JwtService;

/**
 * Unit tests for {@link AuthServiceImpl}.
 * Mocks all dependencies to test business logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;
    private RefreshToken testRefreshToken;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("hashedPassword");
        testUser.setRole(UserRole.CUSTOMER);
        testUser.setVerified(true);

        testRefreshToken = new RefreshToken();
        testRefreshToken.setId(UUID.randomUUID());
        testRefreshToken.setUser(testUser);
        testRefreshToken.setToken("secure-refresh-token");
        testRefreshToken.setExpiresAt(Instant.now().plusSeconds(604800)); // 7 days
        testRefreshToken.setRevoked(false);

        // Default mock behaviors - use lenient() to avoid UnnecessaryStubbingException
        lenient().when(jwtProperties.accessTokenExpiration()).thenReturn(900000L); // 15 minutes
        lenient().when(jwtProperties.refreshTokenExpiration()).thenReturn(604800000L); // 7 days
    }

    // ==================== REGISTER TESTS ====================

    @Test
    void register_ShouldCreateUserAndReturnTokens_WhenEmailIsUnique() {
        // Arrange
        RegisterRequest request = new RegisterRequest("newuser@example.com", "password123");

        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword123");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateToken(anyString())).thenReturn("mock-access-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(testRefreshToken);

        // Act
        AuthResponse response = authService.register(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("mock-access-token");
        assertThat(response.refreshToken()).isEqualTo("secure-refresh-token");
        assertThat(response.expiresInMs()).isEqualTo(900000L);

        // Verify user creation
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("newuser@example.com");
        assertThat(savedUser.getPasswordHash()).isEqualTo("hashedPassword123");
        assertThat(savedUser.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(savedUser.isVerified()).isTrue();

        // Verify token generation
        verify(jwtService).generateToken(anyString());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void register_ShouldThrowBusinessException_WhenEmailAlreadyExists() {
        // Arrange
        RegisterRequest request = new RegisterRequest("existing@example.com", "password123");
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Email already in use")
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getErrorCode()).isEqualTo("EMAIL_ALREADY_EXISTS");
                    assertThat(bex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        // Verify no user was created
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    // ==================== LOGIN TESTS ====================

    @Test
    void login_ShouldReturnTokens_WhenCredentialsAreValid() {
        // Arrange
        LoginRequest request = new LoginRequest("test@example.com", "password123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null); // Successful authentication
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken("test@example.com")).thenReturn("mock-access-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(testRefreshToken);

        // Act
        AuthResponse response = authService.login(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("mock-access-token");
        assertThat(response.refreshToken()).isEqualTo("secure-refresh-token");
        assertThat(response.expiresInMs()).isEqualTo(900000L);

        // Verify authentication was attempted
        verify(authenticationManager).authenticate(
                argThat(auth -> "test@example.com".equals(auth.getPrincipal()) &&
                        "password123".equals(auth.getCredentials())));
        verify(jwtService).generateToken("test@example.com");
    }

    @Test
    void login_ShouldThrowException_WhenUserNotFound() {
        // Arrange
        LoginRequest request = new LoginRequest("nonexistent@example.com", "password123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null); // Authentication passed
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(jwtService, never()).generateToken(anyString());
    }

    // ==================== REFRESH TOKEN TESTS ====================

    @Test
    void refresh_ShouldRotateTokens_WhenRefreshTokenIsValid() {
        // Arrange
        RefreshTokenRequest request = new RefreshTokenRequest("secure-refresh-token");

        when(refreshTokenRepository.findByToken("secure-refresh-token"))
                .thenReturn(Optional.of(testRefreshToken));
        when(jwtService.generateToken("test@example.com")).thenReturn("new-access-token");

        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setToken("new-refresh-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(newRefreshToken);

        // Act
        AuthResponse response = authService.refresh(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");

        // Verify old token was revoked
        verify(refreshTokenRepository).save(argThat(RefreshToken::isRevoked));
        // Verify new token was created
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void refresh_ShouldThrowException_WhenRefreshTokenNotFound() {
        // Arrange
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-token");
        when(refreshTokenRepository.findByToken("invalid-token")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Refresh token not found");

        verify(jwtService, never()).generateToken(anyString());
    }

    @Test
    void refresh_ShouldRevokeAllTokensAndThrow_WhenTokenIsAlreadyRevoked() {
        // Arrange
        testRefreshToken.setRevoked(true);
        RefreshTokenRequest request = new RefreshTokenRequest("secure-refresh-token");

        when(refreshTokenRepository.findByToken("secure-refresh-token"))
                .thenReturn(Optional.of(testRefreshToken));
        when(refreshTokenRepository.findAllValidTokensByUser(testUser.getId()))
                .thenReturn(List.of(testRefreshToken));

        // Act & Assert
        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Security Alert: Reuse of revoked token detected");

        // Verify all tokens were revoked (security measure)
        verify(refreshTokenRepository).findAllValidTokensByUser(testUser.getId());
        verify(refreshTokenRepository).saveAll(anyList());
    }

    @Test
    void refresh_ShouldThrowException_WhenTokenIsExpired() {
        // Arrange
        testRefreshToken.setExpiresAt(Instant.now().minusSeconds(3600)); // Expired 1 hour ago
        RefreshTokenRequest request = new RefreshTokenRequest("secure-refresh-token");

        when(refreshTokenRepository.findByToken("secure-refresh-token"))
                .thenReturn(Optional.of(testRefreshToken));

        // Act & Assert
        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Refresh token expired");

        verify(jwtService, never()).generateToken(anyString());
    }

    // ==================== LOGOUT TESTS ====================

    @Test
    void logout_ShouldRevokeAllUserTokens_WhenUserExists() {
        // Arrange
        RefreshToken token1 = new RefreshToken();
        token1.setRevoked(false);
        RefreshToken token2 = new RefreshToken();
        token2.setRevoked(false);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(refreshTokenRepository.findAllValidTokensByUser(testUser.getId()))
                .thenReturn(List.of(token1, token2));

        // Act
        authService.logout("test@example.com");

        // Assert
        verify(refreshTokenRepository).findAllValidTokensByUser(testUser.getId());
        verify(refreshTokenRepository).saveAll(argThat(tokens -> {
            List<RefreshToken> tokenList = (List<RefreshToken>) tokens;
            return tokenList.size() == 2 &&
                    tokenList.stream().allMatch(RefreshToken::isRevoked);
        }));
    }

    @Test
    void logout_ShouldThrowException_WhenUserNotFound() {
        // Arrange
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.logout("nonexistent@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(refreshTokenRepository, never()).findAllValidTokensByUser(any());
    }

    @Test
    void logout_ShouldHandleNoActiveTokens_Gracefully() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(refreshTokenRepository.findAllValidTokensByUser(testUser.getId()))
                .thenReturn(List.of()); // No active tokens

        // Act
        authService.logout("test@example.com");

        // Assert - Should not throw, just return
        verify(refreshTokenRepository).findAllValidTokensByUser(testUser.getId());
        verify(refreshTokenRepository, never()).saveAll(anyList());
    }
}
