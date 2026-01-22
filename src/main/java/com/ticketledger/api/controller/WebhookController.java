package com.ticketledger.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketledger.constant.RouteConstant;
import com.ticketledger.dto.PaymentWebhookRequest;
import com.ticketledger.service.BookingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(path = RouteConstant.WEBHOOK_PATH, version = RouteConstant.API_VERSION_V1)
@RequiredArgsConstructor
public class WebhookController {
    private final BookingService bookingService;

    @PostMapping("/payment")
    public ResponseEntity<Void> handlePaymentWebhook(@RequestBody PaymentWebhookRequest request) {

        bookingService.processPaymentWebhook(request);
        return ResponseEntity.ok().build();
    }

}
