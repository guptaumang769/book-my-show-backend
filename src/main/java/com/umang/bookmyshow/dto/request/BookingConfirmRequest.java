package com.umang.bookmyshow.dto.request;

import jakarta.validation.constraints.NotBlank;
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
public class BookingConfirmRequest {

    @NotBlank
    private String paymentMethod;

    private Map<String, Object> paymentDetails;

    /**
     * Optional client-supplied key that makes confirmation safe to retry: a repeated
     * confirm with the same key will not charge the card twice (see PaymentService).
     */
    private String idempotencyKey;
}
