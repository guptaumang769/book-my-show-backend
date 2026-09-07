package com.umang.bookmyshow.event;

import com.umang.bookmyshow.config.KafkaConfig;
import com.umang.bookmyshow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventListener {

    private final NotificationService notificationService;

    @KafkaListener(topics = KafkaConfig.BOOKING_EVENTS_TOPIC,
            containerFactory = "kafkaListenerContainerFactory")
    public void onBookingEvent(BookingEvent event) {
        log.debug("Received {} for booking {}", event.getEventType(), event.getBookingId());
        notificationService.handle(event);
    }

    @KafkaListener(topics = KafkaConfig.BOOKING_EVENTS_DLT,
            containerFactory = "kafkaListenerContainerFactory")
    public void onDeadLetter(BookingEvent event) {
        log.error("Dead-lettered event {} for booking {} — needs manual inspection",
                event.getEventType(), event.getBookingId());
    }
}
