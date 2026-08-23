package com.umang.bookmyshow.payment;

import com.umang.bookmyshow.dto.request.PaymentRequest;
import com.umang.bookmyshow.model.enums.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Stub Stripe integration — always succeeds. Swap the body for the real SDK later. */
@Component
public class StripePaymentGateway implements PaymentGateway {

    @Override
    public GatewayResponse processPayment(PaymentRequest request) {
        return GatewayResponse.ok("stripe_" + UUID.randomUUID());
    }

    @Override
    public RefundResponse processRefund(String gatewayTransactionId, BigDecimal amount) {
        return RefundResponse.ok("stripe_re_" + UUID.randomUUID());
    }

    @Override
    public PaymentStatus getPaymentStatus(String transactionId) {
        return PaymentStatus.COMPLETED;
    }

    @Override
    public GatewayType getType() {
        return GatewayType.STRIPE;
    }
}
