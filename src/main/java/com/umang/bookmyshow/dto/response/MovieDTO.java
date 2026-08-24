package com.umang.bookmyshow.dto.response;

import java.time.LocalDate;
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
public class MovieDTO {

    private Long id;
    private String title;
    private String description;
    private Integer durationMinutes;
    private String genre;
    private String language;
    private LocalDate releaseDate;
    private String rating;
    private String posterUrl;
    private String trailerUrl;
}
