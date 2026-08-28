package com.umang.bookmyshow.payment;

import com.umang.bookmyshow.dto.request.PaymentRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resilience4j guard around the payment-gateway call: retry transient failures, open the circuit
 * when the gateway is down, and fall back to a typed failure instead of an exception. It's a
 * separate bean because the annotations only fire through the Spring proxy — a self-call would
 * bypass them. Config: {@code resilience4j.*} in application.yml, instance "paymentGateway".
 */
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

    /** Called when retries are exhausted or the breaker is open. */
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
