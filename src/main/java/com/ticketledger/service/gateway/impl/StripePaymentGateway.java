package com.ticketledger.service.gateway.impl;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.ticketledger.config.StripeProperties;
import com.ticketledger.domain.enums.PaymentStatus;
import com.ticketledger.dto.RefundResponse;
import com.ticketledger.exception.BusinessException;
import com.ticketledger.exception.PermanentGatewayException;
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

            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey)
                    .build();

            Refund refund = Refund.create(params, options);

            return new RefundResponse(
                    refund.getId(),
                    refund.getStatus().toUpperCase(),
                    BigDecimal.valueOf(refund.getAmount() / 100.0),
                    refund.toJson());
        } catch (StripeException e) {
            log.error("Stripe refund failed", e);

            Integer statusCode = e.getStatusCode();
            String stripeCode = e.getStripeError() != null ? e.getStripeError().getCode() : null;

            // Already-refunded is idempotent success from our domain perspective.
            if ("charge_already_refunded".equals(stripeCode)) {
                log.info("Stripe charge already refunded for txn={}, treating as successful", providerTransactionId);
                return new RefundResponse(
                        "already_refunded_" + providerTransactionId,
                        "SUCCEEDED",
                        amount,
                        "{\"status\":\"succeeded\",\"reason\":\"charge_already_refunded\"}");
            }

            if (statusCode != null && statusCode >= 400 && statusCode < 500) {
                HttpStatus httpStatus = HttpStatus.resolve(statusCode);
                if (httpStatus == null) {
                    httpStatus = HttpStatus.BAD_REQUEST;
                }
                throw new PermanentGatewayException(
                        "Permanent payment gateway error: " + e.getMessage(),
                        "PAYMENT_GATEWAY_PERMANENT_ERROR",
                        httpStatus);
            }

            throw new BusinessException(
                    "Payment gateway error: " + e.getMessage(),
                    "PAYMENT_GATEWAY_ERROR",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    @Override
    public PaymentStatus verifyPaymentStatus(String providerTransactionId) throws PermanentGatewayException {
        if (isMockMode()) {
            log.info("Simulating Stripe payment status check for txn: {}", providerTransactionId);
            return PaymentStatus.SUCCESS;
        }

        if (providerTransactionId == null || providerTransactionId.isBlank()) {
            throw new PermanentGatewayException("Provider Transaction ID is missing", "INVALID_PROVIDER_ID",
                    HttpStatus.BAD_REQUEST);
        }

        try {
            PaymentIntent intent = PaymentIntent.retrieve(providerTransactionId);
            String status = intent.getStatus();
            log.info("Stripe payment status for txn {}: {}", providerTransactionId, status);

            return switch (status) {
                case "succeeded" -> PaymentStatus.SUCCESS;
                // These mean the user failed or gave up. Scheduler should EXPIRE the hold.
                case "requires_payment_method", "canceled" -> PaymentStatus.FAILED;
                // These mean the payment is still "in flight". Scheduler should WAIT.
                case "requires_action", "processing" -> PaymentStatus.PENDING;
                default -> PaymentStatus.PENDING;
            };

        } catch (StripeException e) {
            log.error("Stripe payment status check failed for txn {}", providerTransactionId, e);

            // If Stripe explicitly says the ID doesn't exist, we can't confirm success.
            // We treat this as FAILED so the Reaper can reclaim the seat.
            if ("resource_missing".equals(e.getStripeError() != null ? e.getStripeError().getCode() : null)) {
                log.warn("Stripe transaction {} not found. Marking as FAILED for cleanup.", providerTransactionId);
                return PaymentStatus.FAILED;
            }

            if (e.getStatusCode() != null && e.getStatusCode() >= 400 && e.getStatusCode() < 500) {
                throw new PermanentGatewayException(
                        "Permanent gateway error: " + e.getMessage(),
                        "PAYMENT_GATEWAY_PERMANENT_ERROR",
                        HttpStatus.valueOf(e.getStatusCode()));
            }

            // For 5xx or timeouts, return PENDING so we don't accidentally expire a booking
            // that might actually be successful but Stripe is just down.
            return PaymentStatus.PENDING;
        }
    }

    @Override
    public RefundResponse fetchRefundStatus(String providerRefundId, String idempotencyKey) {
        if (isMockMode()) {
            return new RefundResponse(
                    providerRefundId != null ? providerRefundId : "re_mock_check_" + UUID.randomUUID(),
                    "SUCCEEDED",
                    BigDecimal.ZERO, // Amount unknown in mock check without context
                    "{\"status\": \"succeeded\", \"mock\": true}");
        }

        try {
            Refund refund = null;
            if (providerRefundId != null) {
                refund = Refund.retrieve(providerRefundId);
            } else {
                // If we don't have the refund ID, we can't easily look it up by idempotency key
                // via the standard retrieves without storing it.
                // However, for this task, the user prompt implies we should try.
                // Realistically, without the ID, we might need to list refunds for a Charge,
                // but we don't have the Charge ID here easily unless passed.
                // Given the constraints, we will assume if ID is missing, we might return
                // unknown
                // or rely on the caller passing the Charge ID if we changed the signature.
                // BUT, looking at the plan: "Call Stripe to fetch the refund status (using the
                // idempotencyKey or providerTransactionId)"
                // We better rely on providerRefundId if available.
                // If strictly only idempotencyKey is available, Stripe doesn't support "get by
                // idempotency key" directly.
                // We would have to rely on the fact that if we retry the creaation with the
                // same key, we get the same object.
                // But that is a side-effect.

                // Let's stick to retrieving by ID if possible.
                // If ID is null, we return FAILED/UNKNOWN.
                throw new BusinessException("Cannot fetch refund status without providerRefundId", "MISSING_REFUND_ID",
                        HttpStatus.BAD_REQUEST);
            }

            return new RefundResponse(
                    refund.getId(),
                    refund.getStatus().toUpperCase(),
                    BigDecimal.valueOf(refund.getAmount() / 100.0),
                    refund.toJson());

        } catch (StripeException e) {
            log.error("Stripe refund fetch failed", e);
            // If 404
            if (e.getStatusCode() == 404) {
                return new RefundResponse(null, "UNKNOWN", BigDecimal.ZERO, "{}");
            }
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
