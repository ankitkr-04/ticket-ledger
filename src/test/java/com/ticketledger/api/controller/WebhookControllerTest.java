package com.ticketledger.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.ticketledger.domain.enums.PaymentStatus;
import com.ticketledger.dto.PaymentWebhookRequest;
import com.ticketledger.payment.adapter.PaymentGatewayWebhookAdapter;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock
    private PaymentGatewayWebhookAdapter webhookAdapter;

    private WebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new WebhookController(webhookAdapter);
    }

    @Test
    void handlePaymentWebhook_shouldDecodeRawBytesAndDelegateToAdapter() {
        UUID paymentId = UUID.randomUUID();
        String payload = "{\"type\":\"payment_intent.succeeded\",\"note\":\"Jos\u00e9 \u4f60\u597d\"}";
        String signature = "t=1710000000,v1=test-signature";

        PaymentWebhookRequest parsedRequest = new PaymentWebhookRequest(
                paymentId,
                PaymentStatus.SUCCESS,
                "pi_controller_test");

        when(webhookAdapter.parseWebhook(eq(payload), eq(signature))).thenReturn(parsedRequest);

        ResponseEntity<Void> response = controller.handlePaymentWebhook(
                payload.getBytes(StandardCharsets.UTF_8),
                signature);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        ArgumentCaptor<PaymentWebhookRequest> dispatchCaptor = ArgumentCaptor.forClass(PaymentWebhookRequest.class);
        verify(webhookAdapter).parseWebhook(eq(payload), eq(signature));
        verify(webhookAdapter).dispatchWebhook(dispatchCaptor.capture());
        assertThat(dispatchCaptor.getValue()).isEqualTo(parsedRequest);
    }

    @Test
    void handlePaymentWebhook_shouldNotDispatchWhenAdapterReturnsNull() {
        String payload = "{\"type\":\"unknown.event\"}";
        String signature = "t=1710000000,v1=test-signature";

        when(webhookAdapter.parseWebhook(eq(payload), eq(signature))).thenReturn(null);

        ResponseEntity<Void> response = controller.handlePaymentWebhook(
                payload.getBytes(StandardCharsets.UTF_8),
                signature);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(webhookAdapter).parseWebhook(eq(payload), eq(signature));
        verify(webhookAdapter, never()).dispatchWebhook(org.mockito.ArgumentMatchers.any());
    }
}
