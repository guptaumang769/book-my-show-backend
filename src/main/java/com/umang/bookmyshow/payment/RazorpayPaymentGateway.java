package com.umang.bookmyshow.payment;

import com.umang.bookmyshow.dto.request.PaymentRequest;
import com.umang.bookmyshow.model.enums.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Stub Razorpay integration — always succeeds. Swap the body for the real SDK later. */
@Component
public class RazorpayPaymentGateway implements PaymentGateway {

    @Override
    public GatewayResponse processPayment(PaymentRequest request) {
        return GatewayResponse.ok("rzp_" + UUID.randomUUID());
    }

    @Override
    public RefundResponse processRefund(String gatewayTransactionId, BigDecimal amount) {
        return RefundResponse.ok("rzp_rfnd_" + UUID.randomUUID());
    }

    @Override
    public PaymentStatus getPaymentStatus(String transactionId) {
        return PaymentStatus.COMPLETED;
    }

    @Override
    public GatewayType getType() {
        return GatewayType.RAZORPAY;
    }
}
