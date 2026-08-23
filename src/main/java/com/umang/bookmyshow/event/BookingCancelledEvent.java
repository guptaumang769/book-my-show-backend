package com.umang.bookmyshow.event;

import com.umang.bookmyshow.model.entity.Booking;

public class BookingCancelledEvent extends AbstractBookingEvent {

    public BookingCancelledEvent() {
    }

    public BookingCancelledEvent(Booking booking) {
        super(booking);
    }

    @Override
    public String getEventType() {
        return "BOOKING_CANCELLED";
    }
}
