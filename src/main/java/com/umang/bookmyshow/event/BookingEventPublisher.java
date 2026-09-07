package com.umang.bookmyshow.event;

import com.umang.bookmyshow.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

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
