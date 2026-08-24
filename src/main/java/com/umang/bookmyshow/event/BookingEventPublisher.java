package com.umang.bookmyshow.event;

import com.umang.bookmyshow.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes booking events to Kafka, keyed by event type.
 *
 * <p>NOTE on the transactional outbox pattern: callers publish AFTER the DB transaction
 * commits (see BookingService), never inside it. Publishing to Kafka inside the JPA
 * transaction is unsafe — the commit and the send are two separate systems, so a crash
 * between them leaves them inconsistent (event sent but row rolled back, or row committed
 * but event lost). A production system writes the event into an {@code outbox} table in the
 * same transaction as the state change, then a separate poller/CDC relays outbox rows to
 * Kafka and marks them sent, giving exactly-once-ish delivery. Here we publish post-commit
 * for simplicity and accept the small at-most-once window; the outbox table is deferred.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventPublisher {

    private final KafkaTemplate<String, BookingEvent> kafkaTemplate;

    public void publish(BookingEvent event) {
        log.info("Publishing {} for booking {}", event.getEventType(), event.getBookingId());
        kafkaTemplate.send(KafkaConfig.BOOKING_EVENTS_TOPIC, event.getEventType(), event);
    }
}
