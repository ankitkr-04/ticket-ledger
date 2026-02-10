package com.ticketledger.service.gateway.impl.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ticketledger.domain.enums.PaymentStatus;

class StripePaymentGatewayTest {

        @Test
        void mapPaymentIntentStatus_shouldMapFailureStatesToFailed() {
                assertThat(StripePaymentGateway.mapPaymentIntentStatus("requires_payment_method"))
                                .isEqualTo(PaymentStatus.FAILED);
                assertThat(StripePaymentGateway.mapPaymentIntentStatus("canceled"))
                                .isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        void mapPaymentIntentStatus_shouldMapKnownAndUnknownStates() {
                assertThat(StripePaymentGateway.mapPaymentIntentStatus("succeeded"))
                                .isEqualTo(PaymentStatus.SUCCESS);
                assertThat(StripePaymentGateway.mapPaymentIntentStatus("requires_action"))
                                .isEqualTo(PaymentStatus.PENDING);
                assertThat(StripePaymentGateway.mapPaymentIntentStatus("processing"))
                                .isEqualTo(PaymentStatus.PENDING);
                assertThat(StripePaymentGateway.mapPaymentIntentStatus("something_else"))
                                .isEqualTo(PaymentStatus.PENDING);
        }
}
