package com.umang.bookmyshow.exception;

import org.springframework.http.HttpStatus;

public class PaymentFailedException extends BookingException {

    public PaymentFailedException(String message) {
        super(message, "PAYMENT_FAILED", HttpStatus.PAYMENT_REQUIRED);
    }
}
