package com.ticketledger.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ticketledger.domain.model.entity.BookingSeat;
import com.ticketledger.domain.model.key.BookingSeatId;

@Repository
public interface BookingSeatRepository extends JpaRepository<BookingSeat,BookingSeatId> {

    
}