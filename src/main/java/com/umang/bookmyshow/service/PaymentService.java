package com.umang.bookmyshow.service;

import com.umang.bookmyshow.dto.request.PaymentRequest;
import com.umang.bookmyshow.exception.PaymentFailedException;
import com.umang.bookmyshow.model.entity.IdempotencyRecord;
import com.umang.bookmyshow.model.entity.Payment;
import com.umang.bookmyshow.model.enums.PaymentStatus;
import com.umang.bookmyshow.payment.GatewayResponse;
import com.umang.bookmyshow.payment.GatewayType;
import com.umang.bookmyshow.payment.PaymentGatewayFactory;
import com.umang.bookmyshow.payment.RefundResponse;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ResilientPaymentGatewayClient gatewayClient;
    private final PaymentGatewayFactory gatewayFactory;
    private final BookingRepository bookingRepository;
    private final IdempotencyRecordRepository idempotencyRepository;

    @Transactional
    public Payment processPayment(PaymentRequest request) {
        String idempotencyKey = request.getIdempotencyKey();

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

    @Transactional
    public void processRefund(Long bookingId) {
        Payment payment = paymentRepository
                .findTopByBookingIdAndStatusOrderByIdDesc(bookingId, PaymentStatus.COMPLETED)
                .orElse(null);
        if (payment == null) {
            log.info("No completed payment found for booking {} — skipping refund", bookingId);
            return;
        }

        GatewayType gatewayType;
        try {
            gatewayType = GatewayType.valueOf(payment.getPaymentGateway());
        } catch (IllegalArgumentException e) {
            log.error("Unknown gateway {} on payment {} — cannot refund",
                    payment.getPaymentGateway(), payment.getId());
            return;
        }

        RefundResponse response = gatewayFactory.getGateway(gatewayType)
                .processRefund(payment.getGatewayTransactionId(), payment.getAmount());

        if (response.isSuccess()) {
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            log.info("Refund {} processed for payment {} (booking {})",
                    response.getRefundId(), payment.getId(), bookingId);
        } else {
            log.error("Refund failed for payment {} (booking {}): {}",
                    payment.getId(), bookingId, response.getError());
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
            log.warn("Idempotency key {} already recorded by a concurrent request", key);
        }
    }
}
