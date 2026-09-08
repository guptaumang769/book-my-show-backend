package com.umang.bookmyshow.payment;

import com.umang.bookmyshow.dto.request.PaymentRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResilientPaymentGatewayClient {

    private final PaymentGatewayFactory gatewayFactory;

    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "processFallback")
    @Retry(name = "paymentGateway")
    public GatewayResponse process(GatewayType gatewayType, PaymentRequest request) {
        PaymentGateway gateway = gatewayFactory.getGateway(gatewayType);
        return gateway.processPayment(request);
    }

    @SuppressWarnings("unused")
    private GatewayResponse processFallback(GatewayType gatewayType, PaymentRequest request,
                                            Throwable t) {
        log.warn("Payment gateway {} unavailable ({}); returning fallback failure",
                gatewayType, t.toString());
        return GatewayResponse.builder()
                .success(false)
                .error("Payment gateway temporarily unavailable — please retry shortly")
                .build();
    }
}
