package com.umang.bookmyshow.event;

import com.umang.bookmyshow.model.entity.Booking;
import com.umang.bookmyshow.model.enums.BookingStatus;

/** Factory that maps a terminal {@link BookingStatus} to its corresponding event. */
public final class BookingEventFactory {

    private BookingEventFactory() {
    }

    public static BookingEvent createEvent(BookingStatus status, Booking booking) {
        return switch (status) {
            case INITIATED -> new BookingInitiatedEvent(booking);
            case CONFIRMED -> new BookingConfirmedEvent(booking);
            case CANCELLED -> new BookingCancelledEvent(booking);
            case EXPIRED -> new BookingExpiredEvent(booking);
            default -> throw new IllegalArgumentException("Unsupported status: " + status);
        };
    }
}
