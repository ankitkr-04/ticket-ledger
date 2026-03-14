package com.ticketledger.payment.adapter;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.ticketledger.config.StripeProperties;
import com.ticketledger.domain.enums.PaymentStatus;
import com.ticketledger.dto.PaymentWebhookRequest;
import com.ticketledger.exception.domain.InvalidWebhookSignatureException;
import com.ticketledger.service.booking.BookingPaymentService;

import lombok.RequiredArgsConstructor;

@Service
@Profile("!test")
@RequiredArgsConstructor
public class StripeWebhookAdapter implements PaymentGatewayWebhookAdapter {

    private final StripeProperties stripeProperties;
    private final BookingPaymentService bookingPaymentService;
    private static final String PAYMENT_INTENT_SUCCEEDED = "payment_intent.succeeded";
    private static final String PAYMENT_INTENT_FAILED = "payment_intent.payment_failed";
    private static final String PAYMENT_ID_METADATA_KEY = "paymentId";

    @Override
    public PaymentWebhookRequest parseWebhook(String payload, String signature) {

        Event event;

        try {
            event = Webhook.constructEvent(
                    payload,
                    signature,
                    stripeProperties.webhook().secret());
        } catch (SignatureVerificationException e) {
            throw new InvalidWebhookSignatureException();
        }

        if (PAYMENT_INTENT_SUCCEEDED.equals(event.getType())) {

            PaymentIntent intent = (PaymentIntent) event
                    .getDataObjectDeserializer()
                    .getObject()
                    .orElseThrow();

            UUID paymentId = UUID.fromString(intent.getMetadata().get(PAYMENT_ID_METADATA_KEY));

            return new PaymentWebhookRequest(
                    paymentId,
                    PaymentStatus.SUCCESS,
                    intent.getId());
        }

        if (PAYMENT_INTENT_FAILED.equals(event.getType())) {

            PaymentIntent intent = (PaymentIntent) event
                    .getDataObjectDeserializer()
                    .getObject()
                    .orElseThrow();

            UUID paymentId = UUID.fromString(intent.getMetadata().get(PAYMENT_ID_METADATA_KEY));

            return new PaymentWebhookRequest(
                    paymentId,
                    PaymentStatus.FAILED,
                    intent.getId());
        }

        return null;
    }

    @Override
    public void dispatchWebhook(PaymentWebhookRequest event) {
        bookingPaymentService.processPaymentWebhook(event);
    }
}