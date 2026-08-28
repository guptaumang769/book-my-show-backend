package com.umang.bookmyshow.scheduler;

import com.umang.bookmyshow.model.entity.Booking;
import com.umang.bookmyshow.model.enums.BookingStatus;
import com.umang.bookmyshow.repository.BookingRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Finds bookings that were INITIATED but never paid past their hold window and hands each to
 * {@link BookingExpiryService} to be expired in its own transaction. (In a multi-instance
 * deployment this sweep should be guarded by a distributed lock such as ShedLock so only one
 * node runs it at a time.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpiryScheduler {

    private final BookingRepository bookingRepository;
    private final BookingExpiryService bookingExpiryService;

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
                bookingExpiryService.expireOne(booking.getId());
            } catch (RuntimeException e) {
                log.error("Failed to expire booking {}", booking.getId(), e);
            }
        }
    }
}
