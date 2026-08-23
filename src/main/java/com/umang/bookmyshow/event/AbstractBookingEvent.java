package com.umang.bookmyshow.event;

import com.umang.bookmyshow.model.entity.Booking;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Common state for booking events. Kept non-final so Jackson can deserialize subtypes. */
@Getter
@NoArgsConstructor
public abstract class AbstractBookingEvent implements BookingEvent {

    private Long bookingId;
    private String bookingReference;
    private Long userId;
    private String userEmail;
    private Instant timestamp;

    protected AbstractBookingEvent(Booking booking) {
        this.bookingId = booking.getId();
        this.bookingReference = booking.getBookingReference();
        if (booking.getUser() != null) {
            this.userId = booking.getUser().getId();
            this.userEmail = booking.getUser().getEmail();
        }
        this.timestamp = Instant.now();
    }
}
