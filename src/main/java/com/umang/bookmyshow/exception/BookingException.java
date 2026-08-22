package com.umang.bookmyshow.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base type for all domain errors. Carries a stable machine-readable {@code errorCode}
 * and the HTTP status the API should return, so the GlobalExceptionHandler can translate
 * any subclass into a consistent error envelope without a big switch.
 */
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
