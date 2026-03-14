package com.ticketledger.api.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketledger.constant.RouteConstant;
import com.ticketledger.dto.PaymentWebhookRequest;
import com.ticketledger.payment.adapter.PaymentGatewayWebhookAdapter;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(RouteConstant.WEBHOOK_PATH)
@RequiredArgsConstructor
public class WebhookController {

    private final PaymentGatewayWebhookAdapter webhookAdapter;

    private static final String STRIPE_SIGNATURE_HEADER = "Stripe-Signature";

    @PostMapping("/payment")
    public ResponseEntity<Void> handlePaymentWebhook(
            @RequestBody byte[] rawPayload,
            @RequestHeader(STRIPE_SIGNATURE_HEADER) String signature) {

        String payload = new String(rawPayload, StandardCharsets.UTF_8);
        PaymentWebhookRequest request = webhookAdapter.parseWebhook(payload, signature);

        if (request != null) {
            webhookAdapter.dispatchWebhook(request);
        }

        return ResponseEntity.ok().build();
    }
}
