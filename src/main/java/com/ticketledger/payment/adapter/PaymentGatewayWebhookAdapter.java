package com.ticketledger.payment.adapter;

import com.ticketledger.dto.PaymentWebhookRequest;

public interface PaymentGatewayWebhookAdapter {

    PaymentWebhookRequest parseWebhook(String payload, String signature);

    void dispatchWebhook(PaymentWebhookRequest event);
}