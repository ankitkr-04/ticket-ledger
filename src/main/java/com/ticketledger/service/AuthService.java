package com.ticketledger.service;

import com.ticketledger.dto.AuthResponse;
import com.ticketledger.dto.LoginRequest;
import com.ticketledger.dto.RefreshTokenRequest;
import com.ticketledger.dto.RegisterRequest;

public interface AuthService {
    AuthResponse login(LoginRequest request);

    AuthResponse register(RegisterRequest request);

    AuthResponse refresh(RefreshTokenRequest request);
}
