package com.umang.bookmyshow.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.bookmyshow.event.BookingEvent;
import com.umang.bookmyshow.model.entity.OutboxEvent;
import com.umang.bookmyshow.repository.OutboxEventRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public void record(BookingEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
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
