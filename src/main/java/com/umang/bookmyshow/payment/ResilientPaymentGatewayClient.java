package com.umang.bookmyshow.payment;

import com.umang.bookmyshow.dto.request.PaymentRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Wraps the outbound payment-gateway call with Resilience4j so a flaky/slow third-party
 * gateway can't take the booking flow down with it.
 *
 * <p>Why a separate bean? Resilience4j (like {@code @Transactional}) works via Spring AOP
 * proxies, so the annotated method must be invoked from <em>another</em> bean — an internal
 * self-call inside PaymentService would bypass the proxy and the annotations would do nothing.
 *
 * <ul>
 *   <li><b>@Retry</b> rides out transient blips (a dropped connection, a brief 5xx) before
 *       giving up — configured to retry a couple of times with backoff.</li>
 *   <li><b>@CircuitBreaker</b> stops hammering a gateway that is clearly down: after enough
 *       failures it "opens" and fast-fails to the {@link #processFallback fallback} instead
 *       of blocking every request on a doomed call, then probes for recovery (half-open).</li>
 *   <li>The <b>fallback</b> converts an exhausted call into a clean, typed failure response
 *       rather than a raw exception bubbling into the booking flow.</li>
 * </ul>
 *
 * Config lives under {@code resilience4j.*} in application.yml (instance name "paymentGateway").
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

    /**
     * Invoked when retries are exhausted or the breaker is open. The trailing Throwable
     * parameter is how Resilience4j hands the fallback the cause.
     */
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
