package com.umang.bookmyshow.exception;

import java.util.List;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class SeatsNotAvailableException extends BookingException {

    private final transient List<Long> unavailableSeatIds;

    public SeatsNotAvailableException(String message, List<Long> unavailableSeatIds) {
        super(message, "SEATS_NOT_AVAILABLE", HttpStatus.CONFLICT);
        this.unavailableSeatIds = unavailableSeatIds;
    }

    public SeatsNotAvailableException(String message) {
        this(message, List.of());
    }
}
