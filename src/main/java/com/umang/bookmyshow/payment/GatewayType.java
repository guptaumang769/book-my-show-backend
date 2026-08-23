package com.umang.bookmyshow.payment;

/** Selects which {@link PaymentGateway} implementation the factory returns. */
public enum GatewayType {
    STRIPE,
    RAZORPAY
}
