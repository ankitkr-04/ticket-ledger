package com.ticketledger.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ticketledger.domain.entity.Booking;
import com.ticketledger.domain.entity.BookingSeat;
import com.ticketledger.domain.key.BookingSeatId;

@Repository
public interface BookingSeatRepository extends JpaRepository<BookingSeat, BookingSeatId> {
    List<BookingSeat> findByBookingId(UUID bookingId);

    @Query("SELECT bs.seat.id FROM BookingSeat bs WHERE bs.booking.id = :bookingId ORDER BY bs.seat.id ASC")
    List<UUID> findSeatIdsByBookingId(@Param("bookingId") UUID bookingId);

    @Query("SELECT bs.booking FROM BookingSeat bs WHERE bs.seat.id IN :seatIds AND bs.booking.status = 'CONFIRMED'")
    List<Booking> findConfirmedBookingsBySeatIds(@Param("seatIds") List<UUID> seatIds);
}