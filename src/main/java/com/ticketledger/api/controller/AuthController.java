package com.ticketledger.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketledger.constant.RouteConstant;
import com.ticketledger.dto.*;
import com.ticketledger.security.AuthenticatedUser;
import com.ticketledger.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(path = RouteConstant.AUTH_PATH, version = RouteConstant.API_VERSION_V1)
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Authenticates a user and returns an Access Token + Refresh Token.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request), "Login successful"));
    }

    /**
     * Registers a new user and automatically logs them in.
     * Returns Access Token + Refresh Token upon successful registration.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(authService.register(request), "Registration successful"));
    }

    /**
     * Rotates the Refresh Token and issues a new Access Token.
     * Expects { "refresh_token": "..." } in the JSON body.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(request), "Token refreshed successfully"));
    }

    /**
     * Revokes all refresh tokens for the authenticated user and invalidates the
     * session.
     * Requires Authorization header with valid JWT.
     */
    @PostMapping(path = "/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        authService.logout(currentUser.getUsername());
        return ResponseEntity.noContent().build();
    }
}