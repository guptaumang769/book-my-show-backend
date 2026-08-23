package com.umang.bookmyshow.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** Normalized result returned by any {@link PaymentGateway}, hiding provider-specific shapes. */
@Getter
@Builder
@AllArgsConstructor
public class GatewayResponse {

    private final boolean success;
    private final String transactionId;
    private final String error;

    public static GatewayResponse ok(String transactionId) {
        return GatewayResponse.builder().success(true).transactionId(transactionId).build();
    }

    public static GatewayResponse failed(String error) {
        return GatewayResponse.builder().success(false).error(error).build();
    }
}
