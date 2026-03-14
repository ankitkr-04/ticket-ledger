package com.ticketledger.payment.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stripe.Stripe;
import com.ticketledger.config.StripeProperties;
import com.ticketledger.domain.enums.PaymentStatus;
import com.ticketledger.dto.PaymentWebhookRequest;
import com.ticketledger.exception.domain.InvalidWebhookSignatureException;
import com.ticketledger.service.booking.BookingPaymentService;

@ExtendWith(MockitoExtension.class)
class StripeWebhookAdapterTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";

    @Mock
    private BookingPaymentService bookingPaymentService;

    private StripeWebhookAdapter adapter;

    @BeforeEach
    void setUp() {
        StripeProperties properties = new StripeProperties(
                "sk_test_dummy",
                new StripeProperties.Webhook(WEBHOOK_SECRET),
                "INR",
                "admin@test.local");
        adapter = new StripeWebhookAdapter(properties, bookingPaymentService);
    }

    @Test
    void parseWebhook_shouldReturnSuccessRequest_whenSignatureAndPayloadAreValid() {
        UUID paymentId = UUID.randomUUID();
        String providerTransactionId = "pi_3MtwBwLkdIwHu7ix28a3tqPa";
        String payload = successEventPayload(paymentId, providerTransactionId);
        String signatureHeader = stripeSignatureHeader(payload, WEBHOOK_SECRET, Instant.now().getEpochSecond());

        PaymentWebhookRequest request = adapter.parseWebhook(payload, signatureHeader);

        assertThat(request).isNotNull();
        assertThat(request.paymentId()).isEqualTo(paymentId);
        assertThat(request.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(request.providerTransactionId()).isEqualTo(providerTransactionId);
    }

    @Test
    void parseWebhook_shouldThrowInvalidWebhookSignatureException_whenSignatureIsInvalid() {
        UUID paymentId = UUID.randomUUID();
        String payload = successEventPayload(paymentId, "pi_invalid_sig_case");

        // Signed with a different secret on purpose.
        String invalidSignatureHeader = stripeSignatureHeader(payload, "whsec_wrong_secret",
                Instant.now().getEpochSecond());

        assertThatThrownBy(() -> adapter.parseWebhook(payload, invalidSignatureHeader))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void parseWebhook_shouldReturnFailedRequest_whenPaymentIntentFailedEventIsReceived() {
        UUID paymentId = UUID.randomUUID();
        String providerTransactionId = "pi_3MtwBwLkdIwHu7ix28a3tqPb";
        String payload = failedEventPayload(paymentId, providerTransactionId);
        String signatureHeader = stripeSignatureHeader(payload, WEBHOOK_SECRET, Instant.now().getEpochSecond());

        PaymentWebhookRequest request = adapter.parseWebhook(payload, signatureHeader);

        assertThat(request).isNotNull();
        assertThat(request.paymentId()).isEqualTo(paymentId);
        assertThat(request.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(request.providerTransactionId()).isEqualTo(providerTransactionId);
    }

    @Test
    void dispatchWebhook_shouldDelegateToBookingPaymentService() {
        PaymentWebhookRequest request = new PaymentWebhookRequest(
                UUID.randomUUID(),
                PaymentStatus.SUCCESS,
                "pi_dispatch_test");

        adapter.dispatchWebhook(request);

        verify(bookingPaymentService).processPaymentWebhook(request);
    }

    private static String successEventPayload(UUID paymentId, String providerTransactionId) {
        return eventPayload("payment_intent.succeeded", paymentId, providerTransactionId);
    }

    private static String failedEventPayload(UUID paymentId, String providerTransactionId) {
        return eventPayload("payment_intent.payment_failed", paymentId, providerTransactionId);
    }

    private static String eventPayload(String eventType, UUID paymentId, String providerTransactionId) {
        long created = Instant.now().getEpochSecond();
        return """
                {
                    \"id\": \"evt_test_123\",
                    \"object\": \"event\",
                    \"api_version\": \"%s\",
                    \"created\": %d,
                    \"data\": {
                        \"object\": {
                            \"id\": \"%s\",
                            \"object\": \"payment_intent\",
                            \"metadata\": {
                                \"paymentId\": \"%s\"
                            }
                        }
                    },
                    \"livemode\": false,
                    \"pending_webhooks\": 1,
                    \"request\": {
                        \"id\": null,
                        \"idempotency_key\": null
                    },
                    \"type\": \"%s\"
                }
                """.formatted(Stripe.API_VERSION, created, providerTransactionId, paymentId, eventType);
    }

    private static String stripeSignatureHeader(String payload, String secret, long timestamp) {
        String signedPayload = timestamp + "." + payload;
        String signature = hmacSha256Hex(secret, signedPayload);
        return "t=%d,v1=%s".formatted(timestamp, signature);
    }

    private static String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate Stripe test signature", ex);
        }
    }
}
