package com.umang.bookmyshow.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
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
public class ShowDTO {

    private Long showId;
    private Long movieId;
    private String movieName;
    private String theaterName;
    private String screenName;
    private LocalDate showDate;
    private LocalTime showTime;
    private LocalTime endTime;
    private BigDecimal basePrice;
    private Integer availableSeats;
    private Integer totalSeats;
    private String status;
}
