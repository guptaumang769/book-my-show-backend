package com.umang.bookmyshow.payment;

import com.umang.bookmyshow.exception.InvalidRequestException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PaymentGatewayFactory {

    private final Map<GatewayType, PaymentGateway> gateways;

    public PaymentGatewayFactory(List<PaymentGateway> gatewayList) {
        this.gateways = gatewayList.stream()
                .collect(Collectors.toMap(PaymentGateway::getType, Function.identity()));
    }

    public PaymentGateway getGateway(GatewayType type) {
        PaymentGateway gateway = gateways.get(type);
        if (gateway == null) {
            throw new InvalidRequestException("Unsupported payment gateway: " + type);
        }
        return gateway;
    }
}
