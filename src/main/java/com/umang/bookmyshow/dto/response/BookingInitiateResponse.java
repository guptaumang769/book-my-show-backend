package com.umang.bookmyshow.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingInitiateResponse {

    private Long bookingId;
    private String bookingReference;
    private ShowDetailsDTO showDetails;
    private List<SeatDTO> seats;
    private BigDecimal totalAmount;
    private Instant expiresAt;
    private long expiresInSeconds;
}
