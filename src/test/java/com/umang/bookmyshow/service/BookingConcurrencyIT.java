package com.umang.bookmyshow.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.umang.bookmyshow.AbstractIntegrationTest;
import com.umang.bookmyshow.dto.request.BookingInitiateRequest;
import com.umang.bookmyshow.model.entity.City;
import com.umang.bookmyshow.model.entity.Movie;
import com.umang.bookmyshow.model.entity.Screen;
import com.umang.bookmyshow.model.entity.Seat;
import com.umang.bookmyshow.model.entity.Show;
import com.umang.bookmyshow.model.entity.ShowSeat;
import com.umang.bookmyshow.model.entity.Theater;
import com.umang.bookmyshow.model.entity.User;
import com.umang.bookmyshow.model.enums.SeatType;
import com.umang.bookmyshow.model.enums.ShowSeatStatus;
import com.umang.bookmyshow.model.enums.ShowStatus;
import com.umang.bookmyshow.repository.BookingRepository;
import com.umang.bookmyshow.repository.CityRepository;
import com.umang.bookmyshow.repository.MovieRepository;
import com.umang.bookmyshow.repository.ScreenRepository;
import com.umang.bookmyshow.repository.SeatRepository;
import com.umang.bookmyshow.repository.ShowRepository;
import com.umang.bookmyshow.repository.ShowSeatRepository;
import com.umang.bookmyshow.repository.TheaterRepository;
import com.umang.bookmyshow.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 20 threads race to book the same seat; asserts exactly one booking wins and the rest fail
 * cleanly — the double-booking guard (Redis + DB locking) under real contention.
 */
class BookingConcurrencyIT extends AbstractIntegrationTest {

    @Autowired private BookingService bookingService;
    @Autowired private UserRepository userRepository;
    @Autowired private CityRepository cityRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private TheaterRepository theaterRepository;
    @Autowired private ScreenRepository screenRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private ShowRepository showRepository;
    @Autowired private ShowSeatRepository showSeatRepository;
    @Autowired private BookingRepository bookingRepository;

    @Test
    void onlyOneBookingWins_whenManyUsersRaceForTheSameSeat() throws Exception {
        int contenders = 20;
        TestFixture fixture = seedShowWithSingleSeat(contenders);

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        List<Callable<Void>> tasks = fixture.userIds.stream()
                .map(userId -> (Callable<Void>) () -> {
                    try {
                        BookingInitiateRequest req = BookingInitiateRequest.builder()
                                .userId(userId)
                                .showId(fixture.showId)
                                .seatIds(List.of(fixture.showSeatId))
                                .build();
                        bookingService.initiateBooking(req);
                        successes.incrementAndGet();
                    } catch (Exception expected) {
                        // Losers get SeatsNotAvailable / lock-conflict — that's the correct outcome.
                        failures.incrementAndGet();
                    }
                    return null;
                })
                .toList();

        pool.invokeAll(tasks);
        pool.shutdown();

        // Exactly one winner, everyone else rejected.
        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(contenders - 1);

        // And the seat itself is no longer AVAILABLE in the DB.
        ShowSeat seat = showSeatRepository.findById(fixture.showSeatId).orElseThrow();
        assertThat(seat.getStatus()).isNotEqualTo(ShowSeatStatus.AVAILABLE);

        // Exactly one booking row exists.
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    private TestFixture seedShowWithSingleSeat(int userCount) {
        City city = new City();
        city.setName("Bengaluru");
        city.setState("Karnataka");
        city = cityRepository.save(city);

        Movie movie = new Movie();
        movie.setTitle("Concurrency: The Movie");
        movie.setIsActive(true);
        movie = movieRepository.save(movie);

        Theater theater = new Theater();
        theater.setName("Race Cinemas");
        theater.setCity(city);
        theater = theaterRepository.save(theater);

        Screen screen = new Screen();
        screen.setTheater(theater);
        screen.setName("Screen 1");
        screen.setTotalSeats(1);
        screen = screenRepository.save(screen);

        Seat seat = new Seat();
        seat.setScreen(screen);
        seat.setRowNum("A");
        seat.setSeatNumber(1);
        seat.setSeatType(SeatType.REGULAR);
        seat = seatRepository.save(seat);

        Show show = new Show();
        show.setMovie(movie);
        show.setScreen(screen);
        show.setShowDate(LocalDate.now().plusDays(1));
        show.setShowTime(LocalTime.of(19, 30));
        show.setEndTime(LocalTime.of(22, 0));
        show.setBasePrice(new BigDecimal("250.00"));
        show.setTotalSeats(1);
        show.setAvailableSeats(1);
        show.setStatus(ShowStatus.ACTIVE);
        show = showRepository.save(show);

        ShowSeat showSeat = new ShowSeat();
        showSeat.setShow(show);
        showSeat.setSeat(seat);
        showSeat.setPrice(new BigDecimal("250.00"));
        showSeat.setStatus(ShowSeatStatus.AVAILABLE);
        showSeat = showSeatRepository.save(showSeat);

        TestFixture fixture = new TestFixture();
        fixture.showId = show.getId();
        fixture.showSeatId = showSeat.getId();
        for (int i = 0; i < userCount; i++) {
            User u = new User();
            u.setEmail("racer" + i + "@test.com");
            u.setPasswordHash("x");
            fixture.userIds.add(userRepository.save(u).getId());
        }
        return fixture;
    }

    private static class TestFixture {
        Long showId;
        Long showSeatId;
        final java.util.List<Long> userIds = new java.util.ArrayList<>();
    }
}
