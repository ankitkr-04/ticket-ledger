package com.ticketledger.service.gateway.impl.stripe;

import java.math.BigDecimal;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.ticketledger.config.StripeProperties;
import com.ticketledger.constant.ErrorCodeConstant;
import com.ticketledger.constant.PaymentGatewayConstant;
import com.ticketledger.domain.enums.PaymentStatus;
import com.ticketledger.dto.RefundResponse;
import com.ticketledger.exception.BusinessException;
import com.ticketledger.exception.common.PermanentGatewayException;
import com.ticketledger.service.gateway.PaymentGateway;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class StripePaymentGateway implements PaymentGateway {

    private final StripeProperties stripeProperties;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeProperties.secretKey();
    }

    @Override
    public RefundResponse refundPayment(String providerTransactionId, BigDecimal amount, String idempotencyKey) {
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
                        PaymentGatewayConstant.STATUS_SUCCEEDED,
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
                        ErrorCodeConstant.PAYMENT_GATEWAY_PERMANENT_ERROR,
                        httpStatus);
            }

            throw new BusinessException(
                    "Payment gateway error: " + e.getMessage(),
                    ErrorCodeConstant.PAYMENT_GATEWAY_ERROR,
                    HttpStatus.BAD_GATEWAY);
        }
    }

    @Override
    public PaymentStatus verifyPaymentStatus(String providerTransactionId) throws PermanentGatewayException {
        if (providerTransactionId == null || providerTransactionId.isBlank()) {
            throw new PermanentGatewayException("Provider Transaction ID is missing",
                    ErrorCodeConstant.INVALID_PROVIDER_ID,
                    HttpStatus.BAD_REQUEST);
        }

        try {
            PaymentIntent intent = PaymentIntent.retrieve(providerTransactionId);
            String status = intent.getStatus();
            log.info("Stripe payment status for txn {}: {}", providerTransactionId, status);

            return mapPaymentIntentStatus(status);

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
                        ErrorCodeConstant.PAYMENT_GATEWAY_PERMANENT_ERROR,
                        HttpStatus.valueOf(e.getStatusCode()));
            }

            return PaymentStatus.PENDING;
        }
    }

    @Override
    public RefundResponse fetchRefundStatus(String providerRefundId, String idempotencyKey) {
        try {
            if (providerRefundId == null) {
                throw new BusinessException("Cannot fetch refund status without providerRefundId",
                        ErrorCodeConstant.MISSING_REFUND_ID,
                        HttpStatus.BAD_REQUEST);
            }
            Refund refund = Refund.retrieve(providerRefundId);

            return new RefundResponse(
                    refund.getId(),
                    refund.getStatus().toUpperCase(),
                    BigDecimal.valueOf(refund.getAmount() / 100.0),
                    refund.toJson());

        } catch (StripeException e) {
            log.error("Stripe refund fetch failed", e);
            if (e.getStatusCode() == 404) {
                return new RefundResponse(null, PaymentGatewayConstant.STATUS_UNKNOWN, BigDecimal.ZERO,
                        PaymentGatewayConstant.EMPTY_METADATA_JSON);
            }
            throw new BusinessException(
                    "Payment gateway error: " + e.getMessage(),
                    ErrorCodeConstant.PAYMENT_GATEWAY_ERROR,
                    HttpStatus.BAD_GATEWAY);
        }
    }

    static PaymentStatus mapPaymentIntentStatus(String status) {
        return switch (status) {
            case "succeeded" -> PaymentStatus.SUCCESS;
            // These mean the user failed or gave up. Scheduler should EXPIRE the hold.
            case "requires_payment_method", "canceled" -> PaymentStatus.FAILED;
            // These mean the payment is still "in flight". Scheduler should WAIT.
            case "requires_action", "processing" -> PaymentStatus.PENDING;
            default -> PaymentStatus.PENDING;
        };
    }
}
