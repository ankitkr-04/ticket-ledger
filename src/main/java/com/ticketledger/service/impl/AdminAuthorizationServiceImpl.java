package com.ticketledger.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketledger.domain.repository.AdminTheaterAccessRepository;
import com.ticketledger.domain.repository.BookingRepository;
import com.ticketledger.domain.repository.ScreenRepository;
import com.ticketledger.domain.repository.ShowtimeRepository;
import com.ticketledger.exception.NotFoundException;
import com.ticketledger.exception.ShowtimeNotFoundException;
import com.ticketledger.exception.TheaterAccessDeniedException;
import com.ticketledger.security.AuthenticatedUser;
import com.ticketledger.service.AdminAuthorizationService;
import com.ticketledger.util.SecurityUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuthorizationServiceImpl implements AdminAuthorizationService {

    private final AdminTheaterAccessRepository adminTheaterAccessRepository;
    private final ScreenRepository screenRepository;
    private final ShowtimeRepository showtimeRepository;
    private final BookingRepository bookingRepository;
    // TheaterRepository is not strictly needed if we trust the IDs from child
    // entities,
    // but good for assertTheaterAccess existence check if we want to be strict
    // (though interface says assert access, not existence).
    // The design doc implies "IfExists" -> Check. If not exists, maybe 403 or 404?
    // Protocol: "If resource missing -> Throw 404". So for assertTheaterAccess, if
    // theater doesn't exist, we should probably 404.
    // However, usually we just check the access table. If the theater doesn't
    // exist, the access won't exist either.
    // So 403 is acceptable for theaterId check if we don't explicitly fetch the
    // theater.

    @Override
    @Transactional(readOnly = true)
    public UUID assertTheaterAccess(UUID theaterId) {
        AuthenticatedUser user = SecurityUtil.getAuthenticatedUser();

        boolean hasAccess = adminTheaterAccessRepository.existsByUserIdAndTheaterId(user.getId(), theaterId);

        if (!hasAccess) {
            throw new TheaterAccessDeniedException("Access denied to theater: " + theaterId);
        }
        return user.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public UUID assertScreenAccess(UUID screenId) {
        UUID theaterId = screenRepository.findTheaterIdById(screenId)
                .orElseThrow(() -> new NotFoundException("Screen not found: " + screenId));

        return assertTheaterAccess(theaterId);
    }

    @Override
    @Transactional(readOnly = true)
    public UUID assertShowtimeAccess(UUID showtimeId) {
        UUID theaterId = showtimeRepository.findTheaterIdById(showtimeId)
                .orElseThrow(() -> new ShowtimeNotFoundException(showtimeId));

        return assertTheaterAccess(theaterId);
    }

    @Override
    @Transactional(readOnly = true)
    public UUID assertBookingAccess(UUID bookingId) {
        UUID theaterId = bookingRepository.findTheaterIdById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));

        return assertTheaterAccess(theaterId);
    }
}
