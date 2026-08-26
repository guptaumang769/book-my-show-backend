package com.umang.bookmyshow.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.umang.bookmyshow.AbstractIntegrationTest;
import com.umang.bookmyshow.dto.request.BookingInitiateRequest;
import com.umang.bookmyshow.dto.response.BookingInitiateResponse;
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
import com.umang.bookmyshow.repository.BookingSeatRepository;
import com.umang.bookmyshow.repository.CityRepository;
import com.umang.bookmyshow.repository.MovieRepository;
import com.umang.bookmyshow.repository.ScreenRepository;
import com.umang.bookmyshow.repository.SeatRepository;
import com.umang.bookmyshow.repository.ShowRepository;
import com.umang.bookmyshow.repository.ShowSeatRepository;
import com.umang.bookmyshow.repository.TheaterRepository;
import com.umang.bookmyshow.repository.UserRepository;
import com.umang.bookmyshow.service.BookingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies the background reclaim job: a booking left INITIATED past its hold window is
 * moved to EXPIRED and its seats are freed. Exercised through the real Spring bean so the
 * {@code @Scheduled} / {@code REQUIRES_NEW} proxy behaviour is what runs.
 *
 * <p>This test cannot use {@code @Transactional} rollback: the reclaim job runs in its own
 * {@code REQUIRES_NEW} transaction and would not see uncommitted test data. So it commits and
 * cleans the booking rows up in {@link #cleanUp()} — otherwise the leftover booking would break
 * {@code BookingConcurrencyIT}'s global-count assertion in the shared Testcontainers DB.
 */
class BookingExpirySchedulerIT extends AbstractIntegrationTest {

    @Autowired private BookingExpiryScheduler scheduler;
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
    @Autowired private BookingSeatRepository bookingSeatRepository;

    @AfterEach
    void cleanUp() {
        // After expiry, show_seats no longer reference the booking (booking_id nulled), so
        // deleting booking_seats then bookings is FK-safe and resets the global booking count.
        bookingSeatRepository.deleteAll();
        bookingRepository.deleteAll();
    }

    @Test
    void expireStaleBookings_freesSeats_andMarksExpired() {
        Fixture f = seedShow();
        BookingInitiateResponse initiated = bookingService.initiateBooking(
                BookingInitiateRequest.builder()
                        .userId(f.userId).showId(f.showId).seatIds(List.of(f.showSeatId)).build());

        // Age the booking so it is eligible for expiry.
        Booking booking = bookingRepository.findById(initiated.getBookingId()).orElseThrow();
        booking.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        bookingRepository.save(booking);

        scheduler.expireStaleBookings();

        Booking after = bookingRepository.findById(initiated.getBookingId()).orElseThrow();
        assertThat(after.getBookingStatus()).isEqualTo(BookingStatus.EXPIRED);

        ShowSeat seat = showSeatRepository.findById(f.showSeatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE);
        assertThat(seat.getBookingId()).isNull();
    }

    private Fixture seedShow() {
        City city = new City();
        city.setName("Pune");
        city.setState("Maharashtra");
        city = cityRepository.save(city);

        Movie movie = new Movie();
        movie.setTitle("Expiry: The Movie");
        movie.setIsActive(true);
        movie = movieRepository.save(movie);

        Theater theater = new Theater();
        theater.setName("Timeout Theatre");
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
        show.setShowTime(LocalTime.of(18, 0));
        show.setEndTime(LocalTime.of(20, 30));
        show.setBasePrice(new BigDecimal("300.00"));
        show.setTotalSeats(1);
        show.setAvailableSeats(1);
        show.setStatus(ShowStatus.ACTIVE);
        show = showRepository.save(show);

        ShowSeat showSeat = new ShowSeat();
        showSeat.setShow(show);
        showSeat.setSeat(seat);
        showSeat.setPrice(new BigDecimal("300.00"));
        showSeat.setStatus(ShowSeatStatus.AVAILABLE);
        showSeat = showSeatRepository.save(showSeat);

        User user = new User();
        user.setEmail("expiry-booker@test.com");
        user.setPasswordHash("x");

        Fixture fixture = new Fixture();
        fixture.showId = show.getId();
        fixture.showSeatId = showSeat.getId();
        fixture.userId = userRepository.save(user).getId();
        return fixture;
    }

    private static class Fixture {
        Long showId;
        Long showSeatId;
        Long userId;
    }
}
