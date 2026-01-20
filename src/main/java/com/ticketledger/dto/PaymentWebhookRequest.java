package com.ticketledger.dto;

import java.util.UUID;

import com.ticketledger.domain.enums.PaymentStatus;

import jakarta.validation.constraints.NotNull;

public record PaymentWebhookRequest(
        @NotNull UUID paymentId,
        @NotNull PaymentStatus status, String providerTransactionId) {

}
