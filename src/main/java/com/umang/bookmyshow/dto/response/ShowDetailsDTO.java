package com.umang.bookmyshow.dto.response;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Compact show descriptor embedded in booking responses (LLD section 6). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowDetailsDTO {

    private Long showId;
    private String movieName;
    private String theaterName;
    private String screenName;
    private LocalDate showDate;
    private String showTime;
}
