package com.umang.bookmyshow.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Custom business metrics for the booking domain.
 *
 * <p>WHY THIS EXISTS: infrastructure metrics (CPU, heap, HTTP latency) tell you the
 * <em>system</em> is healthy; they do NOT tell you the <em>business</em> is healthy. A server
 * can be at 5% CPU with p99 latency of 20ms and still be silently failing every booking. Custom
 * business metrics close that gap by measuring what the product actually does.
 *
 * <p>These meters follow the RED method (Rate / Errors / Duration): the counters give the RATE
 * of each outcome (initiated, confirmed, cancelled, expired) and the ERROR-adjacent signals
 * (seat.lock.contention = demand exceeding supply), while the Timer gives the DURATION of the
 * money-path confirm step. Ratios of these counters become KPIs — e.g. confirmed/initiated is the
 * checkout conversion rate, expired/initiated is the abandonment rate — which you can alert on and
 * put on a Grafana business dashboard next to the infra panels.
 *
 * <p>Micrometer is a vendor-neutral facade (SLF4J-for-metrics): the same MeterRegistry API is
 * exported to Prometheus here, but could publish to Datadog/CloudWatch without changing this class.
 */
@Component
public class BookingMetrics {

    private final Counter initiated;
    private final Counter confirmed;
    private final Counter cancelled;
    private final Counter expired;
    private final Counter seatLockContention;
    private final Timer confirmDuration;

    public BookingMetrics(MeterRegistry registry) {
        // Counters are monotonic — Prometheus stores the cumulative total and PromQL rate() derives
        // per-second throughput from it. Dot-separated names are Micrometer-idiomatic and get
        // translated to Prometheus snake_case (e.g. bookings_initiated_total) at scrape time.
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
        // A Timer records BOTH a count and a latency distribution; with percentile histograms enabled
        // it lets Grafana render p95/p99 of the confirm (payment) path — the RED "Duration" signal.
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

    /** Called wherever a lock/seat-availability acquisition loses a race. */
    public void recordSeatLockContention() {
        seatLockContention.increment();
    }

    /** Times the supplied confirm work and records it under booking.confirm.duration. */
    public <T> T timeConfirm(Supplier<T> work) {
        return confirmDuration.record(work);
    }
}
