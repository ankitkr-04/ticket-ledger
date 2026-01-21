package com.ticketledger.util;

import com.ticketledger.exception.TicketLedgerException;
import com.ticketledger.security.AuthenticatedUser;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SecurityUtil {

    public static AuthenticatedUser getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() 
            || !(authentication.getPrincipal() instanceof AuthenticatedUser)) {
            // This implies a configuration error or an unauthorized request reaching a protected service
            throw new TicketLedgerException("No authenticated user found in SecurityContext", 
                "INTERNAL_SECURITY_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, Map.of()) {
            };
        }

        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
