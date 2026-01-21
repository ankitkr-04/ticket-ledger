package com.ticketledger.service.gateway.impl;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;
import com.ticketledger.config.StripeProperties;
import com.ticketledger.dto.RefundResponse;
import com.ticketledger.exception.BusinessException;
import com.ticketledger.service.gateway.PaymentGateway;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripePaymentGateway implements PaymentGateway {

    private final StripeProperties stripeProperties;

    @PostConstruct
    public void init() {
        if (!isMockMode()) {
            Stripe.apiKey = stripeProperties.secretKey();
        }
    }

    @Override
    public RefundResponse refundPayment(String providerTransactionId, BigDecimal amount, String idempotencyKey) {
        if (isMockMode()) {
            log.info("Simulating Stripe refund for txn: {}, amount: {}", providerTransactionId, amount);
            return new RefundResponse(
                    "re_mock_" + UUID.randomUUID(),
                    "SUCCEEDED",
                    amount,
                    "{\"status\": \"succeeded\", \"mock\": true}");
        }

        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setCharge(providerTransactionId)
                    .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue()) // Stripe expects cents
                    .build();
            // Note: Stripe SDK supports idempotency via RequestOptions but we rely on
            // database locks for simple cases first

            Refund refund = Refund.create(params);

            return new RefundResponse(
                    refund.getId(),
                    refund.getStatus().toUpperCase(),
                    BigDecimal.valueOf(refund.getAmount() / 100.0),
                    refund.toJson());
        } catch (StripeException e) {
            log.error("Stripe refund failed", e);
            throw new BusinessException(
                    "Payment gateway error: " + e.getMessage(),
                    "PAYMENT_GATEWAY_ERROR",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    private boolean isMockMode() {
        String key = stripeProperties.secretKey();
        return key == null || "mock".equalsIgnoreCase(key) || "test".equalsIgnoreCase(key);
    }
}
