package com.umang.bookmyshow.payment;

import com.umang.bookmyshow.dto.request.PaymentRequest;
import com.umang.bookmyshow.model.enums.PaymentStatus;

/**
 * Strategy interface for payment providers. Adding a new provider (e.g. a UPI PSP)
 * means implementing this and annotating it {@code @Component} with its own
 * {@link #getType()} — no change to callers or the factory.
 */
public interface PaymentGateway {

    GatewayResponse processPayment(PaymentRequest request);

    RefundResponse processRefund(String gatewayTransactionId, java.math.BigDecimal amount);

    PaymentStatus getPaymentStatus(String transactionId);

    /** The key this gateway registers itself under in the factory. */
    GatewayType getType();
}
