package com.umang.bookmyshow.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BookingException extends RuntimeException {

    private final String errorCode;
    private final transient HttpStatus httpStatus;

    public BookingException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
