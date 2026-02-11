package com.ticketledger.service.gateway.impl.stripe;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.ticketledger.domain.enums.GatewayStatus;
import com.ticketledger.domain.enums.PaymentStatus;
import com.ticketledger.dto.RefundResponse;
import com.ticketledger.service.gateway.PaymentGateway;

import lombok.extern.slf4j.Slf4j;

@Service
@Profile("test")
@Slf4j
public class MockStripePaymentGateway implements PaymentGateway {

    @Override
    public RefundResponse refundPayment(String providerTransactionId, BigDecimal amount, String idempotencyKey) {
        log.info("Simulating Stripe refund for txn: {}, amount: {}", providerTransactionId, amount);
        return new RefundResponse(
                "re_mock_" + UUID.randomUUID(),
                GatewayStatus.SUCCEEDED,
                amount,
                "{\"status\": \"succeeded\", \"mock\": true}");
    }

    @Override
    public RefundResponse fetchRefundStatus(String providerRefundId, String idempotencyKey) {
        return new RefundResponse(
                providerRefundId != null ? providerRefundId : "re_mock_check_" + UUID.randomUUID(),
                GatewayStatus.SUCCEEDED,
                BigDecimal.ZERO,
                "{\"status\": \"succeeded\", \"mock\": true}");
    }

    @Override
    public PaymentStatus verifyPaymentStatus(String providerTransactionId) {
        log.info("Simulating Stripe payment status check for txn: {}", providerTransactionId);
        return PaymentStatus.SUCCESS;
    }
}
