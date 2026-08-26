package com.umang.bookmyshow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.umang.bookmyshow.AbstractIntegrationTest;
import com.umang.bookmyshow.dto.request.BookingConfirmRequest;
import com.umang.bookmyshow.dto.request.BookingInitiateRequest;
import com.umang.bookmyshow.dto.response.BookingConfirmResponse;
import com.umang.bookmyshow.dto.response.BookingInitiateResponse;
import com.umang.bookmyshow.exception.BookingExpiredException;
import com.umang.bookmyshow.exception.InvalidRequestException;
import com.umang.bookmyshow.model.entity.Booking;
import com.umang.bookmyshow.model.entity.City;
import com.umang.bookmyshow.model.entity.Movie;
import com.umang.bookmyshow.model.entity.Screen;
import com.umang.bookmyshow.model.entity.Seat;
import com.umang.bookmyshow.model.entity.Show;
import com.umang.bookmyshow.model.entity.ShowSeat;
import com.umang.bookmyshow.model.entity.Theater;
import com.umang.bookmyshow.model.entity.User;
import com.umang.bookmyshow.model.enums.BookingStatus;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full-lifecycle integration tests for {@link BookingService} against real Postgres + Redis
 * (Testcontainers). These cover the paths the concurrency test does not: confirm, cancel,
 * expiry rejection, the state machine's illegal transitions, and the ownership check.
 *
 * <p>{@code @Transactional} rolls back each test's writes at the end, so nothing persists into
 * the shared Testcontainers DB — keeping these isolated from sibling ITs (notably
 * {@code BookingConcurrencyIT}, which asserts a global booking count). Every service call here
 * uses default (REQUIRED) propagation, so it joins and rolls back with the test transaction.
 */
@Transactional
class BookingServiceIT extends AbstractIntegrationTest {

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
    void confirmBooking_marksBooked_onHappyPath() {
        Fixture f = seedShow(2);
        BookingInitiateResponse initiated = bookingService.initiateBooking(
                BookingInitiateRequest.builder()
                        .userId(f.userId).showId(f.showId).seatIds(f.showSeatIds).build());

        BookingConfirmResponse confirmed = bookingService.confirmBooking(
                initiated.getBookingId(),
                BookingConfirmRequest.builder()
                        .paymentMethod("UPI")
                        .idempotencyKey("confirm-happy-1")
                        .build());

        assertThat(confirmed.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED.name());
        // Every seat in the booking is now BOOKED.
        List<ShowSeat> seats = showSeatRepository.findByShowId(f.showId).stream()
                .filter(s -> initiated.getBookingId().equals(s.getBookingId()))
                .toList();
        assertThat(seats).isNotEmpty();
        assertThat(seats).allMatch(s -> s.getStatus() == ShowSeatStatus.BOOKED);
    }

    @Test
    void confirmBooking_throws_whenBookingHasExpired() {
        Fixture f = seedShow(1);
        BookingInitiateResponse initiated = bookingService.initiateBooking(
                BookingInitiateRequest.builder()
                        .userId(f.userId).showId(f.showId).seatIds(f.showSeatIds).build());

        // Force the hold window into the past so confirm must reject it.
        Booking booking = bookingRepository.findById(initiated.getBookingId()).orElseThrow();
        booking.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        bookingRepository.save(booking);

        assertThatThrownBy(() -> bookingService.confirmBooking(
                initiated.getBookingId(),
                BookingConfirmRequest.builder().paymentMethod("UPI").build()))
                .isInstanceOf(BookingExpiredException.class);
    }

    @Test
    void cancelBooking_freesSeats_andSetsCancelled() {
        Fixture f = seedShow(2);
        BookingInitiateResponse initiated = bookingService.initiateBooking(
                BookingInitiateRequest.builder()
                        .userId(f.userId).showId(f.showId).seatIds(f.showSeatIds).build());

        bookingService.cancelBooking(initiated.getBookingId(), f.userId);

        Booking booking = bookingRepository.findById(initiated.getBookingId()).orElseThrow();
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        // Seats are released back to AVAILABLE and detached from the booking.
        List<ShowSeat> seats = showSeatRepository.findByShowId(f.showId);
        assertThat(seats).allMatch(s -> s.getStatus() == ShowSeatStatus.AVAILABLE);
        assertThat(seats).allMatch(s -> s.getBookingId() == null);
    }

    @Test
    void cancelBooking_throws_onIllegalStateTransition() {
        Fixture f = seedShow(1);
        BookingInitiateResponse initiated = bookingService.initiateBooking(
                BookingInitiateRequest.builder()
                        .userId(f.userId).showId(f.showId).seatIds(f.showSeatIds).build());

        bookingService.cancelBooking(initiated.getBookingId(), f.userId);

        // CANCELLED -> CANCELLED is not a legal transition; the state machine must reject it.
        assertThatThrownBy(() ->
                bookingService.cancelBooking(initiated.getBookingId(), f.userId))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void getBookingDetails_throws_whenBookingBelongsToAnotherUser() {
        Fixture f = seedShow(1);
        BookingInitiateResponse initiated = bookingService.initiateBooking(
                BookingInitiateRequest.builder()
                        .userId(f.userId).showId(f.showId).seatIds(f.showSeatIds).build());

        Long strangerId = userRepository.save(newUser("stranger@test.com")).getId();

        assertThatThrownBy(() ->
                bookingService.getBookingDetails(initiated.getBookingId(), strangerId))
                .isInstanceOf(InvalidRequestException.class);
    }

    // ---- fixture helpers ---------------------------------------------------

    private User newUser(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("x");
        return u;
    }

    private Fixture seedShow(int seatCount) {
        City city = new City();
        city.setName("Bengaluru");
        city.setState("Karnataka");
        city = cityRepository.save(city);

        Movie movie = new Movie();
        movie.setTitle("Lifecycle: The Movie");
        movie.setIsActive(true);
        movie = movieRepository.save(movie);

        Theater theater = new Theater();
        theater.setName("Lifecycle Cinemas");
        theater.setCity(city);
        theater = theaterRepository.save(theater);

        Screen screen = new Screen();
        screen.setTheater(theater);
        screen.setName("Screen 1");
        screen.setTotalSeats(seatCount);
        screen = screenRepository.save(screen);

        Show show = new Show();
        show.setMovie(movie);
        show.setScreen(screen);
        show.setShowDate(LocalDate.now().plusDays(1));
        show.setShowTime(LocalTime.of(19, 30));
        show.setEndTime(LocalTime.of(22, 0));
        show.setBasePrice(new BigDecimal("250.00"));
        show.setTotalSeats(seatCount);
        show.setAvailableSeats(seatCount);
        show.setStatus(ShowStatus.ACTIVE);
        show = showRepository.save(show);

        Fixture fixture = new Fixture();
        fixture.showId = show.getId();
        for (int i = 0; i < seatCount; i++) {
            Seat seat = new Seat();
            seat.setScreen(screen);
            seat.setRowNum("A");
            seat.setSeatNumber(i + 1);
            seat.setSeatType(SeatType.REGULAR);
            seat = seatRepository.save(seat);

            ShowSeat showSeat = new ShowSeat();
            showSeat.setShow(show);
            showSeat.setSeat(seat);
            showSeat.setPrice(new BigDecimal("250.00"));
            showSeat.setStatus(ShowSeatStatus.AVAILABLE);
            showSeat = showSeatRepository.save(showSeat);
            fixture.showSeatIds.add(showSeat.getId());
        }
        fixture.userId = userRepository.save(newUser("booker@test.com")).getId();
        return fixture;
    }

    private static class Fixture {
        Long showId;
        Long userId;
        final List<Long> showSeatIds = new java.util.ArrayList<>();
    }
}
