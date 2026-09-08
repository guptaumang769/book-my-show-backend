package com.umang.bookmyshow.payment;

import com.umang.bookmyshow.dto.request.PaymentRequest;
import com.umang.bookmyshow.model.enums.PaymentStatus;

public interface PaymentGateway {

    GatewayResponse processPayment(PaymentRequest request);

    RefundResponse processRefund(String gatewayTransactionId, java.math.BigDecimal amount);

    PaymentStatus getPaymentStatus(String transactionId);

    GatewayType getType();
}
