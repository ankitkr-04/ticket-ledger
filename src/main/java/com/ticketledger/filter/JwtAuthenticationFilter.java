package com.ticketledger.filter;

import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ticketledger.constant.SecurityConstant;
import com.ticketledger.security.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // 1. Early exit if no valid header is present
        if (authHeader == null || !authHeader.startsWith(SecurityConstant.BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            jwt = authHeader.substring(SecurityConstant.BEARER_PREFIX_LENGTH);

            // 2. Extract username (this will throw ExpiredJwtException if access token is
            // expired)
            userEmail = jwtService.extractUsername(jwt);

            // 3. Authentication Check
            // We only authenticate if the SecurityContext is currently empty
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                // 4. Validate Token
                // Note: This validates signature and expiration.
                // In a robust setup, you might also check a "token_type" claim here
                // to ensure this isn't a Refresh Token being used as an Access Token.
                if (jwtService.isTokenValid(jwt, userDetails)) {

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null, // Credentials (password) are null for JWT auth
                            userDetails.getAuthorities());

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 5. Update Security Context
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Log the error but DO NOT throw it.
            // If we throw, we break the filter chain for public endpoints (like /refresh).
            // By catching and continuing, the SecurityContext remains empty.
            // If the endpoint required auth, Spring Security will trigger the 401 later.
            log.debug("JWT Filter failed validation: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}