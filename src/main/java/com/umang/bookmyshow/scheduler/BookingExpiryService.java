package com.umang.bookmyshow.scheduler;

import com.umang.bookmyshow.event.BookingExpiredEvent;
import com.umang.bookmyshow.model.entity.Booking;
import com.umang.bookmyshow.model.entity.ShowSeat;
import com.umang.bookmyshow.model.enums.BookingStatus;
import com.umang.bookmyshow.model.enums.ShowSeatStatus;
import com.umang.bookmyshow.observability.BookingMetrics;
import com.umang.bookmyshow.outbox.OutboxService;
import com.umang.bookmyshow.repository.BookingRepository;
import com.umang.bookmyshow.repository.ShowRepository;
import com.umang.bookmyshow.repository.ShowSeatRepository;
import com.umang.bookmyshow.service.SeatLockService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expires a single stale booking in its own transaction. This lives in a separate bean from
 * {@link BookingExpiryScheduler} on purpose: {@code @Transactional(REQUIRES_NEW)} (and the
 * cache eviction) only take effect through the Spring proxy, so the scheduler must call it as
 * a collaborator — an internal self-call would bypass the proxy and silently drop both.
 */
@Service
@RequiredArgsConstructor
public class BookingExpiryService {

    private final BookingRepository bookingRepository;
    private final ShowSeatRepository showSeatRepository;
    private final ShowRepository showRepository;
    private final SeatLockService seatLockService;
    private final OutboxService outboxService;
    private final BookingMetrics bookingMetrics;

    /**
     * Expires one booking in its OWN transaction so a single bad row can't roll back the whole
     * sweep. Freeing seats raises availability, so the cached seat maps are evicted.
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = "show_seats", allEntries = true),
            @CacheEvict(cacheNames = "shows", allEntries = true)
    })
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireOne(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || booking.getBookingStatus() != BookingStatus.INITIATED) {
            return;
        }

        booking.setBookingStatus(BookingStatus.EXPIRED);
        bookingRepository.save(booking);

        Long showId = booking.getShow().getId();
        List<ShowSeat> seats = showSeatRepository.findByBookingId(bookingId);
        seats.forEach(s -> {
            s.setStatus(ShowSeatStatus.AVAILABLE);
            s.setLockedAt(null);
            s.setLockedBy(null);
            s.setBookingId(null);
        });
        showSeatRepository.saveAll(seats);
        showRepository.incrementAvailableSeats(showId, seats.size());

        List<Long> seatIds = seats.stream().map(ShowSeat::getId).toList();
        Long ownerId = booking.getUser() != null ? booking.getUser().getId() : null;
        seatLockService.releaseLocks(showId, seatIds, ownerId);
        outboxService.record(new BookingExpiredEvent(booking));
        bookingMetrics.recordExpired();
    }
}
