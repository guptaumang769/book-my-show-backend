package com.umang.bookmyshow.event;

import com.umang.bookmyshow.model.entity.Booking;

public class BookingInitiatedEvent extends AbstractBookingEvent {

    public BookingInitiatedEvent() {
    }

    public BookingInitiatedEvent(Booking booking) {
        super(booking);
    }

    @Override
    public String getEventType() {
        return "BOOKING_INITIATED";
    }
}
