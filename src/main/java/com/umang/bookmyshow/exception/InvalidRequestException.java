package com.umang.bookmyshow.exception;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends BookingException {

    public InvalidRequestException(String message) {
        super(message, "INVALID_REQUEST", HttpStatus.BAD_REQUEST);
    }
}
