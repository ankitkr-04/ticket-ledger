package com.ticketledger.payment.adapter;

import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.ticketledger.constant.ErrorCodeConstant;
import com.ticketledger.domain.enums.PaymentStatus;
import com.ticketledger.dto.PaymentWebhookRequest;
import com.ticketledger.exception.BusinessException;
import com.ticketledger.service.booking.BookingPaymentService;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@Profile("test")
@RequiredArgsConstructor
public class TestWebhookAdapter implements PaymentGatewayWebhookAdapter {

    private static final String PAYMENT_INTENT_SUCCEEDED = "payment_intent.succeeded";
    private static final String PAYMENT_INTENT_FAILED = "payment_intent.payment_failed";
    private static final String PAYMENT_ID_METADATA_KEY = "paymentId";

    private final BookingPaymentService bookingPaymentService;
    private final JsonMapper jsonMapper;

    @Override
    public PaymentWebhookRequest parseWebhook(String payload, String signature) {
        JsonNode root;
        try {
            root = jsonMapper.readTree(payload);
        } catch (JacksonException ex) {
            throw new BusinessException(
                    "Malformed webhook payload",
                    ErrorCodeConstant.INVALID_REQUEST,
                    HttpStatus.BAD_REQUEST,
                    Map.of("reason", "Invalid JSON payload"));
        }

        String eventType = textOrNull(root.path("type"));
        if (isBlank(eventType)) {
            return null;
        }

        JsonNode paymentIntent = root.path("data").path("object");
        String paymentIdValue = textOrNull(paymentIntent.path("metadata").path(PAYMENT_ID_METADATA_KEY));
        String providerTransactionId = textOrNull(paymentIntent.path("id"));

        if (isBlank(paymentIdValue)) {
            return null;
        }

        UUID paymentId;
        try {
            paymentId = UUID.fromString(paymentIdValue);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(
                    "Invalid paymentId in webhook payload",
                    ErrorCodeConstant.INVALID_REQUEST,
                    HttpStatus.BAD_REQUEST,
                    Map.of("paymentId", paymentIdValue));
        }

        if (PAYMENT_INTENT_SUCCEEDED.equals(eventType)) {
            return new PaymentWebhookRequest(paymentId, PaymentStatus.SUCCESS, providerTransactionId);
        }

        if (PAYMENT_INTENT_FAILED.equals(eventType)) {
            return new PaymentWebhookRequest(paymentId, PaymentStatus.FAILED, providerTransactionId);
        }

        return null;
    }

    @Override
    public void dispatchWebhook(PaymentWebhookRequest event) {
        bookingPaymentService.processPaymentWebhook(event);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asString();
    }
}
