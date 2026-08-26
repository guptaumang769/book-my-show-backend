package com.umang.bookmyshow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umang.bookmyshow.dto.request.PaymentRequest;
import com.umang.bookmyshow.exception.PaymentFailedException;
import com.umang.bookmyshow.model.entity.Payment;
import com.umang.bookmyshow.model.enums.PaymentStatus;
import com.umang.bookmyshow.payment.GatewayResponse;
import com.umang.bookmyshow.payment.GatewayType;
import com.umang.bookmyshow.payment.ResilientPaymentGatewayClient;
import com.umang.bookmyshow.repository.BookingRepository;
import com.umang.bookmyshow.repository.IdempotencyRecordRepository;
import com.umang.bookmyshow.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The declined-payment path: when the gateway returns a failure, the service must throw
 * {@link PaymentFailedException} and persist the payment as FAILED (so it isn't silently lost
 * and the booking is never confirmed).
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceFailureTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private ResilientPaymentGatewayClient gatewayClient;
    @Mock private BookingRepository bookingRepository;
    @Mock private IdempotencyRecordRepository idempotencyRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository, gatewayClient, bookingRepository, idempotencyRepository);
    }

    @Test
    void processPayment_marksFailed_andThrows_whenGatewayDeclines() {
        when(idempotencyRepository.findByIdempotencyKey("key-decline")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(200L);
            }
            return p;
        });
        // Gateway says "no".
        when(gatewayClient.process(any(), any()))
                .thenReturn(GatewayResponse.failed("card_declined"));

        PaymentRequest request = PaymentRequest.builder()
                .bookingId(1L)
                .amount(new BigDecimal("750.00"))
                .paymentMethod("CARD")
                .gatewayType(GatewayType.STRIPE)
                .idempotencyKey("key-decline")
                .build();

        assertThatThrownBy(() -> paymentService.processPayment(request))
                .isInstanceOf(PaymentFailedException.class);

        // The persisted payment must end up FAILED (captured from the last save).
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }
}
