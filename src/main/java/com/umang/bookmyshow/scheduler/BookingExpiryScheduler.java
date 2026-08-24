package com.umang.bookmyshow.scheduler;

import com.umang.bookmyshow.event.BookingExpiredEvent;
import com.umang.bookmyshow.outbox.OutboxService;
import com.umang.bookmyshow.model.entity.Booking;
import com.umang.bookmyshow.model.entity.ShowSeat;
import com.umang.bookmyshow.model.enums.BookingStatus;
import com.umang.bookmyshow.model.enums.ShowSeatStatus;
import com.umang.bookmyshow.observability.BookingMetrics;
import com.umang.bookmyshow.repository.BookingRepository;
import com.umang.bookmyshow.repository.ShowRepository;
import com.umang.bookmyshow.repository.ShowSeatRepository;
import com.umang.bookmyshow.service.SeatLockService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reclaims seats from bookings that were INITIATED but never paid (LLD 3.3). Runs on a fixed
 * delay; each booking is expired in its OWN transaction (REQUIRES_NEW) so one bad row cannot
 * roll back the whole batch. Redis lock release and event publish happen after commit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpiryScheduler {

    private final BookingRepository bookingRepository;
    private final ShowSeatRepository showSeatRepository;
    private final ShowRepository showRepository;
    private final SeatLockService seatLockService;
    private final OutboxService outboxService;
    private final BookingMetrics bookingMetrics;

    @Scheduled(fixedDelayString = "${booking.expiry.fixed-delay-ms:60000}")
    public void expireStaleBookings() {
        List<Booking> expired = bookingRepository.findByBookingStatusAndExpiresAtBefore(
                BookingStatus.INITIATED, Instant.now());
        if (expired.isEmpty()) {
            return;
        }
        log.info("Expiring {} stale booking(s)", expired.size());
        for (Booking booking : expired) {
            try {
                expireOne(booking.getId());
            } catch (RuntimeException e) {
                log.error("Failed to expire booking {}", booking.getId(), e);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireOne(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || booking.getBookingStatus() != BookingStatus.INITIATED) {
            return;
        }

        booking.setBookingStatus(BookingStatus.EXPIRED);
        bookingRepository.save(booking);

        Long showId = booking.getShow().getId();
        List<ShowSeat> seats = showSeatRepository.findByShowId(showId).stream()
                .filter(s -> bookingId.equals(s.getBookingId()))
                .toList();
        seats.forEach(s -> {
            s.setStatus(ShowSeatStatus.AVAILABLE);
            s.setLockedAt(null);
            s.setLockedBy(null);
            s.setBookingId(null);
        });
        showSeatRepository.saveAll(seats);
        showRepository.incrementAvailableSeats(showId, seats.size());

        List<Long> seatIds = seats.stream().map(ShowSeat::getId).toList();
        seatLockService.releaseLocks(showId, seatIds);
        outboxService.record(new BookingExpiredEvent(booking));
        bookingMetrics.recordExpired();
    }
}
