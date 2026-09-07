package com.umang.bookmyshow.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class BookingMetrics {

    private final Counter initiated;
    private final Counter confirmed;
    private final Counter cancelled;
    private final Counter expired;
    private final Counter seatLockContention;
    private final Timer confirmDuration;

    public BookingMetrics(MeterRegistry registry) {
        this.initiated = Counter.builder("bookings.initiated")
                .description("Bookings successfully moved to INITIATED (seats locked)")
                .register(registry);
        this.confirmed = Counter.builder("bookings.confirmed")
                .description("Bookings confirmed after successful payment")
                .register(registry);
        this.cancelled = Counter.builder("bookings.cancelled")
                .description("Bookings cancelled by the user")
                .register(registry);
        this.expired = Counter.builder("bookings.expired")
                .description("Bookings expired by the scheduler (initiated but never paid)")
                .register(registry);
        this.seatLockContention = Counter.builder("seat.lock.contention")
                .description("Seat lock acquisition failures — two users racing for the same seats")
                .register(registry);
        this.confirmDuration = Timer.builder("booking.confirm.duration")
                .description("End-to-end latency of the confirmBooking path (payment + writes)")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void recordInitiated() {
        initiated.increment();
    }

    public void recordConfirmed() {
        confirmed.increment();
    }

    public void recordCancelled() {
        cancelled.increment();
    }

    public void recordExpired() {
        expired.increment();
    }

    public void recordSeatLockContention() {
        seatLockContention.increment();
    }

    public <T> T timeConfirm(Supplier<T> work) {
        return confirmDuration.record(work);
    }
}
