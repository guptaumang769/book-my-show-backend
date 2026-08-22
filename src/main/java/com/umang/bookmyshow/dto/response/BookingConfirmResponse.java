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
public class BookingConfirmResponse {

    private Long bookingId;
    private String bookingReference;
    private String bookingStatus;
    private String paymentStatus;
    private String paymentId;
    private Instant confirmedAt;
    private String ticketUrl;
    private ShowDetailsDTO showDetails;
    private List<SeatDTO> seats;
    private BigDecimal totalAmount;
}
