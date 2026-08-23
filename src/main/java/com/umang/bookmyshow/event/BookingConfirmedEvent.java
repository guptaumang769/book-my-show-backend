package com.umang.bookmyshow.event;

import com.umang.bookmyshow.model.entity.Booking;

public class BookingConfirmedEvent extends AbstractBookingEvent {

    public BookingConfirmedEvent() {
    }

    public BookingConfirmedEvent(Booking booking) {
        super(booking);
    }

    @Override
    public String getEventType() {
        return "BOOKING_CONFIRMED";
    }
}
