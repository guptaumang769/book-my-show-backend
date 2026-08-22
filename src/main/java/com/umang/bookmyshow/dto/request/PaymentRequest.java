package com.umang.bookmyshow.dto.request;

import com.umang.bookmyshow.payment.GatewayType;
import java.math.BigDecimal;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    private Long bookingId;
    private BigDecimal amount;
    private String paymentMethod;
    private GatewayType gatewayType;
    private Map<String, Object> paymentDetails;

    /** Makes the charge idempotent across retries. */
    private String idempotencyKey;
}
