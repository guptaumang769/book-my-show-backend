package com.umang.bookmyshow.event;

import com.umang.bookmyshow.model.entity.Booking;

public class BookingExpiredEvent extends AbstractBookingEvent {

    public BookingExpiredEvent() {
    }

    public BookingExpiredEvent(Booking booking) {
        super(booking);
    }

    @Override
    public String getEventType() {
        return "BOOKING_EXPIRED";
    }
}
