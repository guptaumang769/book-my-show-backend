package com.umang.bookmyshow.service;

import com.umang.bookmyshow.event.BookingEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stub notification sink. Invoked by the Kafka listener; a real implementation would
 * fan out to an email/SMS provider. Kept side-effect-free (just logs) for the portfolio.
 */
@Slf4j
@Service
public class NotificationService {

    public void handle(BookingEvent event) {
        switch (event.getEventType()) {
            case "BOOKING_CONFIRMED" -> log.info(
                    "email/SMS sent: booking {} confirmed", event.getBookingId());
            case "BOOKING_CANCELLED" -> log.info(
                    "email/SMS sent: booking {} cancelled", event.getBookingId());
            case "BOOKING_EXPIRED" -> log.info(
                    "email/SMS sent: booking {} expired, seats released", event.getBookingId());
            case "BOOKING_INITIATED" -> log.info(
                    "email/SMS sent: booking {} initiated, complete payment soon", event.getBookingId());
            default -> log.info("email/SMS sent: {} for booking {}",
                    event.getEventType(), event.getBookingId());
        }
    }
}
