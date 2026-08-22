package com.umang.bookmyshow.repository;

import com.umang.bookmyshow.model.entity.Booking;
import com.umang.bookmyshow.model.enums.BookingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Booking> findByBookingReference(String bookingReference);

    /** Used by the expiry scheduler to reclaim seats from stale INITIATED bookings. */
    List<Booking> findByBookingStatusAndExpiresAtBefore(BookingStatus status, Instant expiryTime);
}
