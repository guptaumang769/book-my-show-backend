package com.umang.bookmyshow.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.bookmyshow.event.BookingEvent;
import com.umang.bookmyshow.model.entity.OutboxEvent;
import com.umang.bookmyshow.repository.OutboxEventRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Write side of the transactional outbox. {@link #record} is called from within a service's
 * {@code @Transactional} method (e.g. BookingService.initiateBooking), so the outbox row and
 * the booking state change commit atomically — no dual-write to Kafka.
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    /** Persist an event to the outbox in the caller's current transaction. */
    public void record(BookingEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // Should never happen for our own event types; fail loud rather than lose the event.
            throw new IllegalStateException("Failed to serialize outbox event", e);
        }
        OutboxEvent row = OutboxEvent.builder()
                .aggregateId(String.valueOf(event.getBookingId()))
                .eventType(event.getEventType())
                .payload(payload)
                .published(false)
                .createdAt(Instant.now())
                .build();
        repository.save(row);
    }
}
