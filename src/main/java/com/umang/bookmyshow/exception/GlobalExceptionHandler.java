package com.umang.bookmyshow.exception;

import com.umang.bookmyshow.dto.response.ErrorDetails;
import com.umang.bookmyshow.dto.response.ErrorResponse;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BookingException.class)
    public ResponseEntity<ErrorResponse> handleBookingException(BookingException ex) {
        List<Long> unavailableSeats = ex instanceof SeatsNotAvailableException snae
                ? snae.getUnavailableSeatIds() : null;
        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .error(ErrorDetails.builder()
                        .code(ex.getErrorCode())
                        .message(ex.getMessage())
                        .unavailableSeats(unavailableSeats != null && !unavailableSeats.isEmpty()
                                ? unavailableSeats : null)
                        .timestamp(Instant.now())
                        .build())
                .build();
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();
        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .error(ErrorDetails.builder()
                        .code("VALIDATION_ERROR")
                        .message("Invalid request parameters")
                        .details(errors)
                        .timestamp(Instant.now())
                        .build())
                .build();
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .error(ErrorDetails.builder()
                        .code("INTERNAL_SERVER_ERROR")
                        .message("An unexpected error occurred")
                        .timestamp(Instant.now())
                        .build())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
