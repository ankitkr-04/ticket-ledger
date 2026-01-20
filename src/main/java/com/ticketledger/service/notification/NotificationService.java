package com.ticketledger.service.notification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ticketledger.domain.event.BookingConfirmedEvent;
import com.ticketledger.domain.event.BookingRefundEvent;
import com.ticketledger.service.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendBookingConfirmation(BookingConfirmedEvent event) {
        emailService.sendBookingConfirmation(event.bookingId(), event.userEmail(), event.amount());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendRefundNotification(BookingRefundEvent event) {
        emailService.sendRefundNotification(event.bookingId(), event.userEmail(), event.amount());
    }

}
