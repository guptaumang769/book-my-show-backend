package com.umang.bookmyshow.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.bookmyshow.config.KafkaConfig;
import com.umang.bookmyshow.event.BookingEvent;
import com.umang.bookmyshow.model.entity.OutboxEvent;
import com.umang.bookmyshow.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relay side of the outbox. Polls unpublished rows and ships them to Kafka via the existing
 * typed template (so the DLT/error handling still applies), then marks them published.
 * Delivery is <b>at-least-once</b>: a crash after the Kafka send but before the DB update
 * re-sends the event on the next poll — which is why consumers must be idempotent.
 * (A production system might use Debezium/CDC instead of polling.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, BookingEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${outbox.poll.fixed-delay-ms:2000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> batch =
                repository.findByPublishedFalseOrderByCreatedAtAsc(Limit.of(BATCH_SIZE));
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxEvent row : batch) {
            try {
                BookingEvent event = objectMapper.readValue(row.getPayload(), BookingEvent.class);
                kafkaTemplate.send(KafkaConfig.BOOKING_EVENTS_TOPIC, row.getAggregateId(), event);
                row.setPublished(true);
                row.setPublishedAt(Instant.now());
            } catch (Exception e) {
                // Leave the row unpublished; it'll be retried next poll. Log and move on so
                // one bad row doesn't block the batch.
                log.error("Failed to relay outbox event {} ({}): {}",
                        row.getId(), row.getEventType(), e.toString());
            }
        }
        repository.saveAll(batch);
        log.debug("Outbox poll processed {} row(s)", batch.size());
    }
}
