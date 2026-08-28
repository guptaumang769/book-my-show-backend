package com.umang.bookmyshow.service;

import com.umang.bookmyshow.dto.request.PaymentRequest;
import com.umang.bookmyshow.exception.PaymentFailedException;
import com.umang.bookmyshow.model.entity.IdempotencyRecord;
import com.umang.bookmyshow.model.entity.Payment;
import com.umang.bookmyshow.model.enums.PaymentStatus;
import com.umang.bookmyshow.payment.GatewayResponse;
import com.umang.bookmyshow.payment.GatewayType;
import com.umang.bookmyshow.payment.ResilientPaymentGatewayClient;
import com.umang.bookmyshow.repository.BookingRepository;
import com.umang.bookmyshow.repository.IdempotencyRecordRepository;
import com.umang.bookmyshow.repository.PaymentRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Charges a booking through a pluggable gateway.
 *
 * <p>IDEMPOTENCY: charging money must be safe to retry. Clients (or an upstream retry on a
 * dropped response) may submit the same payment twice. We key each attempt by an
 * idempotency key: on the first call we persist the outcome under that key; on any replay
 * with the same key we return the stored result and never call the gateway again — so the
 * customer is charged at most once. The unique constraint on idempotency_keys is the final
 * guard against a race between two concurrent replays. This class is deliberately generic
 * (no UPI/ticketing specifics) so it can be lifted into a future UPI payment project.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ResilientPaymentGatewayClient gatewayClient;
    private final BookingRepository bookingRepository;
    private final IdempotencyRecordRepository idempotencyRepository;

    @Transactional
    public Payment processPayment(PaymentRequest request) {
        String idempotencyKey = request.getIdempotencyKey();

        // Replay short-circuit: same key already processed -> return the stored payment.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRecord> existing =
                    idempotencyRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent() && existing.get().getPaymentId() != null) {
                log.info("Idempotent replay for key {} -> returning stored payment {}",
                        idempotencyKey, existing.get().getPaymentId());
                return paymentRepository.getReferenceById(existing.get().getPaymentId());
            }
        }

        Payment payment = Payment.builder()
                .booking(bookingRepository.getReferenceById(request.getBookingId()))
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod())
                .status(PaymentStatus.PENDING)
                .build();
        payment = paymentRepository.save(payment);

        try {
            GatewayType gatewayType =
                    request.getGatewayType() != null ? request.getGatewayType() : GatewayType.STRIPE;
            payment.setPaymentGateway(gatewayType.name());

            // Resilient call: retry + circuit breaker + fallback (see ResilientPaymentGatewayClient).
            GatewayResponse response = gatewayClient.process(gatewayType, request);
            if (response.isSuccess()) {
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setGatewayTransactionId(response.getTransactionId());
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                recordIdempotency(idempotencyKey, request, payment);
                throw new PaymentFailedException(response.getError());
            }
            payment = paymentRepository.save(payment);
            recordIdempotency(idempotencyKey, request, payment);
            return payment;
        } catch (PaymentFailedException e) {
            throw e;
        } catch (RuntimeException e) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw new PaymentFailedException("Payment processing error: " + e.getMessage());
        }
    }

    private void recordIdempotency(String key, PaymentRequest request, Payment payment) {
        if (key == null || key.isBlank()) {
            return;
        }
        IdempotencyRecord idempotencyRecord = IdempotencyRecord.builder()
                .idempotencyKey(key)
                .bookingId(request.getBookingId())
                .paymentId(payment.getId())
                .gatewayTransactionId(payment.getGatewayTransactionId())
                .paymentStatus(payment.getStatus().name())
                .build();
        try {
            idempotencyRepository.save(idempotencyRecord);
        } catch (DataIntegrityViolationException e) {
            // A concurrent replay committed the same key first; the unique constraint did its
            // job. Treat it as already-recorded rather than surfacing a 500 to the caller.
            log.warn("Idempotency key {} already recorded by a concurrent request", key);
        }
    }
}
