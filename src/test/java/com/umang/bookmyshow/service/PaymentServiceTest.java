package com.umang.bookmyshow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umang.bookmyshow.dto.request.PaymentRequest;
import com.umang.bookmyshow.model.entity.IdempotencyRecord;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies the core idempotency guarantee: a repeated payment with the same idempotency key
 * must NOT hit the gateway a second time. This is the property that stops a retried/duplicated
 * "confirm" request from charging a customer twice — the single most important payment invariant.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

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

    private PaymentRequest request(String idemKey) {
        return PaymentRequest.builder()
                .bookingId(1L)
                .amount(new BigDecimal("750.00"))
                .paymentMethod("UPI")
                .gatewayType(GatewayType.STRIPE)
                .idempotencyKey(idemKey)
                .build();
    }

    @Test
    void processPayment_chargesGateway_whenIdempotencyKeyIsNew() {
        when(idempotencyRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(gatewayClient.process(any(), any())).thenReturn(
                GatewayResponse.builder().success(true).transactionId("txn-1").build());
        // save() must return a non-null Payment (with an id) — the service reassigns
        // `payment = paymentRepository.save(payment)`, so a null here NPEs downstream.
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(100L);
            }
            return p;
        });

        Payment payment = paymentService.processPayment(request("key-1"));

        assertThat(payment).isNotNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getGatewayTransactionId()).isEqualTo("txn-1");
        verify(gatewayClient).process(any(), any());
    }

    @Test
    void processPayment_doesNotChargeGatewayAgain_whenIdempotencyKeyReplayed() {
        // The stored record MUST carry a paymentId — that's what the service checks to decide
        // a key was already processed (existing.getPaymentId() != null). Without it the replay
        // short-circuit doesn't trigger and the payment is (wrongly) charged again.
        IdempotencyRecord existing = IdempotencyRecord.builder()
                .idempotencyKey("key-1")
                .bookingId(1L)
                .paymentId(100L)
                .gatewayTransactionId("txn-1")
                .paymentStatus("COMPLETED")
                .build();
        Payment stored = Payment.builder().id(100L).status(PaymentStatus.COMPLETED).build();
        when(idempotencyRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));
        when(paymentRepository.getReferenceById(100L)).thenReturn(stored);

        Payment result = paymentService.processPayment(request("key-1"));

        // Returns the stored payment and NEVER touches the gateway (no double charge).
        assertThat(result).isSameAs(stored);
        verify(gatewayClient, never()).process(any(), any());
    }
}
