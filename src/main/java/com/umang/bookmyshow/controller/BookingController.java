package com.umang.bookmyshow.controller;

import com.umang.bookmyshow.dto.request.BookingConfirmRequest;
import com.umang.bookmyshow.dto.request.BookingInitiateRequest;
import com.umang.bookmyshow.dto.response.ApiResponse;
import com.umang.bookmyshow.dto.response.BookingConfirmResponse;
import com.umang.bookmyshow.dto.response.BookingDetailsResponse;
import com.umang.bookmyshow.dto.response.BookingInitiateResponse;
import com.umang.bookmyshow.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @Operation(summary = "Initiate a booking: lock seats and start the 10-minute payment window")
    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<BookingInitiateResponse>> initiate(
            @Valid @RequestBody BookingInitiateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.initiateBooking(request)));
    }

    @Operation(summary = "Confirm a booking after payment")
    @PutMapping("/{bookingId}/confirm")
    public ResponseEntity<ApiResponse<BookingConfirmResponse>> confirm(
            @PathVariable Long bookingId,
            @Valid @RequestBody BookingConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                bookingService.confirmBooking(bookingId, request)));
    }

    @Operation(summary = "Cancel a booking and release its seats")
    @DeleteMapping("/{bookingId}")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable Long bookingId,
            @RequestParam Long userId) {
        bookingService.cancelBooking(bookingId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "List a user's bookings")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BookingDetailsResponse>>> getUserBookings(
            @RequestParam Long userId) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getUserBookings(userId)));
    }

    @Operation(summary = "Get a single booking's details")
    @GetMapping("/{bookingId}")
    public ResponseEntity<ApiResponse<BookingDetailsResponse>> getBooking(
            @PathVariable Long bookingId,
            @RequestParam Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                bookingService.getBookingDetails(bookingId, userId)));
    }
}
