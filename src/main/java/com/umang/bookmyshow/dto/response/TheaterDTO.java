package com.umang.bookmyshow.dto.response;

import java.math.BigDecimal;
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
public class TheaterDTO {

    private Long id;
    private String name;
    private String cityName;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer totalScreens;
}
