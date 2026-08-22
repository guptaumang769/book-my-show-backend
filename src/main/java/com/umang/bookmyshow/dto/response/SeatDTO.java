package com.umang.bookmyshow.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A single seat line item: {@code {"seatId", "row", "number", "type", "price"}}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatDTO {

    private Long seatId;
    private String row;
    private Integer number;
    private String type;
    private BigDecimal price;
}
