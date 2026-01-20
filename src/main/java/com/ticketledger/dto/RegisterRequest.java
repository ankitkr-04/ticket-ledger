package com.ticketledger.dto;

import com.ticketledger.domain.enums.UserRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for user registration.
 * Supports both CUSTOMER (default) and ADMIN registration.
 */
public record RegisterRequest(
                @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,

                @NotBlank(message = "Password is required") @Size(min = 8, message = "Password must be at least 8 characters") String password,

                UserRole role,
                
                String fullName,
                
                String profileImageUrl) {

        /**
         * Get role with default fallback to CUSTOMER
         */
        public UserRole getRole() {
                return role != null ? role : UserRole.CUSTOMER;
        }
}
