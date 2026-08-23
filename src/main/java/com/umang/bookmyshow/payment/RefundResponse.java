package com.umang.bookmyshow.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class RefundResponse {

    private final boolean success;
    private final String refundId;
    private final String error;

    public static RefundResponse ok(String refundId) {
        return RefundResponse.builder().success(true).refundId(refundId).build();
    }

    public static RefundResponse failed(String error) {
        return RefundResponse.builder().success(false).error(error).build();
    }
}
