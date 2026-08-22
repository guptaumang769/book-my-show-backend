package com.umang.bookmyshow.exception;

import org.springframework.http.HttpStatus;

public class BookingExpiredException extends BookingException {

    public BookingExpiredException(Long bookingId) {
        super("Booking " + bookingId + " has expired. Please book again.",
                "BOOKING_EXPIRED", HttpStatus.BAD_REQUEST);
    }
}
