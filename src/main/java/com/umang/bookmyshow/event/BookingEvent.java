package com.umang.bookmyshow.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;

/**
 * Marker for all booking domain events. The Jackson type info makes the concrete
 * subtype recoverable on the Kafka consumer side after JSON (de)serialization.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BookingInitiatedEvent.class, name = "BOOKING_INITIATED"),
        @JsonSubTypes.Type(value = BookingConfirmedEvent.class, name = "BOOKING_CONFIRMED"),
        @JsonSubTypes.Type(value = BookingCancelledEvent.class, name = "BOOKING_CANCELLED"),
        @JsonSubTypes.Type(value = BookingExpiredEvent.class, name = "BOOKING_EXPIRED")
})
public interface BookingEvent {

    String getEventType();

    Long getBookingId();

    Instant getTimestamp();
}
