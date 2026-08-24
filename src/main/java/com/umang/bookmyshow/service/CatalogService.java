package com.umang.bookmyshow.service;

import com.umang.bookmyshow.dto.response.MovieDTO;
import com.umang.bookmyshow.dto.response.SeatDTO;
import com.umang.bookmyshow.dto.response.ShowDTO;
import com.umang.bookmyshow.dto.response.ShowDetailsDTO;
import com.umang.bookmyshow.dto.response.ShowSeatsResponse;
import com.umang.bookmyshow.exception.ResourceNotFoundException;
import com.umang.bookmyshow.model.entity.Movie;
import com.umang.bookmyshow.model.entity.Show;
import com.umang.bookmyshow.model.entity.ShowSeat;
import com.umang.bookmyshow.model.enums.ShowSeatStatus;
import com.umang.bookmyshow.repository.MovieRepository;
import com.umang.bookmyshow.repository.ShowRepository;
import com.umang.bookmyshow.repository.ShowSeatRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CatalogService {

    private final MovieRepository movieRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    @Cacheable(value = "movies", key = "#genre == null ? 'all' : #genre")
    public List<MovieDTO> getMoviesByCity(Long cityId, String genre, String language) {
        List<Movie> movies = (genre == null || genre.isBlank())
                ? movieRepository.findByIsActiveTrue()
                : movieRepository.findByGenreIgnoreCaseAndIsActiveTrue(genre);
        return movies.stream()
                .filter(m -> language == null || language.isBlank()
                        || language.equalsIgnoreCase(m.getLanguage()))
                .map(this::toMovieDto)
                .toList();
    }

    @Cacheable(value = "shows", key = "#movieId + '-' + #cityId + '-' + #date",
            unless = "#result.isEmpty()")
    public List<ShowDTO> getShowsForMovie(Long movieId, Long cityId, LocalDate date) {
        return showRepository.findShows(movieId, cityId, date).stream()
                .map(this::toShowDto)
                .toList();
    }

    @Cacheable(value = "show_seats", key = "#showId", unless = "#result == null")
    public ShowSeatsResponse getAvailableSeats(Long showId) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show", showId));
        List<ShowSeat> availableSeats =
                showSeatRepository.findByShowIdAndStatus(showId, ShowSeatStatus.AVAILABLE);
        return ShowSeatsResponse.builder()
                .showDetails(toShowDetails(show))
                .availableSeats(availableSeats.stream().map(this::toSeatDto).toList())
                .availableCount(availableSeats.size())
                .build();
    }

    private MovieDTO toMovieDto(Movie m) {
        return MovieDTO.builder()
                .id(m.getId())
                .title(m.getTitle())
                .description(m.getDescription())
                .durationMinutes(m.getDurationMinutes())
                .genre(m.getGenre())
                .language(m.getLanguage())
                .releaseDate(m.getReleaseDate())
                .rating(m.getRating())
                .posterUrl(m.getPosterUrl())
                .trailerUrl(m.getTrailerUrl())
                .build();
    }

    private ShowDTO toShowDto(Show s) {
        return ShowDTO.builder()
                .showId(s.getId())
                .movieId(s.getMovie() != null ? s.getMovie().getId() : null)
                .movieName(s.getMovie() != null ? s.getMovie().getTitle() : null)
                .theaterName(theaterName(s))
                .screenName(s.getScreen() != null ? s.getScreen().getName() : null)
                .showDate(s.getShowDate())
                .showTime(s.getShowTime())
                .endTime(s.getEndTime())
                .basePrice(s.getBasePrice())
                .availableSeats(s.getAvailableSeats())
                .totalSeats(s.getTotalSeats())
                .status(s.getStatus() != null ? s.getStatus().name() : null)
                .build();
    }

    private ShowDetailsDTO toShowDetails(Show s) {
        return ShowDetailsDTO.builder()
                .showId(s.getId())
                .movieName(s.getMovie() != null ? s.getMovie().getTitle() : null)
                .theaterName(theaterName(s))
                .screenName(s.getScreen() != null ? s.getScreen().getName() : null)
                .showDate(s.getShowDate())
                .showTime(s.getShowTime() != null ? s.getShowTime().toString() : null)
                .build();
    }

    private String theaterName(Show s) {
        return s.getScreen() != null && s.getScreen().getTheater() != null
                ? s.getScreen().getTheater().getName() : null;
    }

    private SeatDTO toSeatDto(ShowSeat ss) {
        return SeatDTO.builder()
                .seatId(ss.getId())
                .row(ss.getSeat() != null ? ss.getSeat().getRowNum() : null)
                .number(ss.getSeat() != null ? ss.getSeat().getSeatNumber() : null)
                .type(ss.getSeat() != null && ss.getSeat().getSeatType() != null
                        ? ss.getSeat().getSeatType().name() : null)
                .price(ss.getPrice())
                .build();
    }
}
