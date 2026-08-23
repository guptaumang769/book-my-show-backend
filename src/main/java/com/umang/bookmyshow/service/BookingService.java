package com.umang.bookmyshow.service;

import com.umang.bookmyshow.dto.request.BookingConfirmRequest;
import com.umang.bookmyshow.dto.request.BookingInitiateRequest;
import com.umang.bookmyshow.dto.request.PaymentRequest;
import com.umang.bookmyshow.dto.response.BookingConfirmResponse;
import com.umang.bookmyshow.dto.response.BookingDetailsResponse;
import com.umang.bookmyshow.dto.response.BookingInitiateResponse;
import com.umang.bookmyshow.dto.response.SeatDTO;
import com.umang.bookmyshow.dto.response.ShowDetailsDTO;
import com.umang.bookmyshow.event.BookingCancelledEvent;
import com.umang.bookmyshow.event.BookingConfirmedEvent;
import com.umang.bookmyshow.event.BookingInitiatedEvent;
import com.umang.bookmyshow.outbox.OutboxService;
import com.umang.bookmyshow.exception.BookingExpiredException;
import com.umang.bookmyshow.exception.InvalidRequestException;
import com.umang.bookmyshow.exception.ResourceNotFoundException;
import com.umang.bookmyshow.exception.SeatsNotAvailableException;
import com.umang.bookmyshow.model.entity.Booking;
import com.umang.bookmyshow.model.entity.BookingSeat;
import com.umang.bookmyshow.model.entity.Payment;
import com.umang.bookmyshow.model.entity.Show;
import com.umang.bookmyshow.model.entity.ShowSeat;
import com.umang.bookmyshow.model.enums.BookingStatus;
import com.umang.bookmyshow.model.enums.PaymentStatus;
import com.umang.bookmyshow.model.enums.ShowSeatStatus;
import com.umang.bookmyshow.observability.BookingMetrics;
import com.umang.bookmyshow.payment.GatewayType;
import com.umang.bookmyshow.repository.BookingRepository;
import com.umang.bookmyshow.repository.BookingSeatRepository;
import com.umang.bookmyshow.repository.ShowRepository;
import com.umang.bookmyshow.repository.ShowSeatRepository;
import com.umang.bookmyshow.repository.UserRepository;
import com.umang.bookmyshow.util.BookingReferenceGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final int EXPIRY_MINUTES = 10;

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final UserRepository userRepository;
    private final SeatLockService seatLockService;
    private final PaymentService paymentService;
    private final OutboxService outboxService;
    private final BookingMetrics bookingMetrics;

    /**
     * Initiate flow (LLD 7.1): grab Redis locks first, then pessimistically lock the
     * show_seat rows, validate availability, LOCK them, create the INITIATED booking with a
     * 10-minute expiry, and decrement the denormalized counter. On any failure the Redis
     * locks are released so the seats free up immediately rather than waiting out the TTL.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public BookingInitiateResponse initiateBooking(BookingInitiateRequest request) {
        request.validate();

        boolean locksAcquired = seatLockService.acquireLocks(
                request.getShowId(), request.getSeatIds(), request.getUserId());
        if (!locksAcquired) {
            // Two users raced for the same seats and this one lost the Redis lock — a demand signal.
            bookingMetrics.recordSeatLockContention();
            throw new SeatsNotAvailableException(
                    "One or more selected seats are no longer available", request.getSeatIds());
        }

        try {
            List<ShowSeat> showSeats = showSeatRepository
                    .findByShowIdAndSeatIdInForUpdate(request.getShowId(), request.getSeatIds());

            List<ShowSeat> availableSeats = showSeats.stream()
                    .filter(seat -> seat.getStatus() == ShowSeatStatus.AVAILABLE)
                    .toList();

            if (availableSeats.size() != request.getSeatIds().size()) {
                List<Long> unavailable = showSeats.stream()
                        .filter(s -> s.getStatus() != ShowSeatStatus.AVAILABLE)
                        .map(ShowSeat::getId)
                        .toList();
                // Lost the race at the DB layer (seats taken between lock and row read).
                bookingMetrics.recordSeatLockContention();
                throw new SeatsNotAvailableException(
                        "One or more selected seats are no longer available", unavailable);
            }

            BigDecimal totalAmount = availableSeats.stream()
                    .map(ShowSeat::getPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Instant now = Instant.now();
            availableSeats.forEach(seat -> {
                seat.setStatus(ShowSeatStatus.LOCKED);
                seat.setLockedAt(now);
                seat.setLockedBy(userRepository.getReferenceById(request.getUserId()));
            });
            showSeatRepository.saveAll(availableSeats);

            Booking booking = Booking.builder()
                    .user(userRepository.getReferenceById(request.getUserId()))
                    .show(showRepository.getReferenceById(request.getShowId()))
                    .bookingReference(BookingReferenceGenerator.generate())
                    .totalAmount(totalAmount)
                    .bookingStatus(BookingStatus.INITIATED)
                    .paymentStatus(PaymentStatus.PENDING)
                    .bookedAt(now)
                    .expiresAt(now.plus(EXPIRY_MINUTES, ChronoUnit.MINUTES))
                    .build();
            final Booking savedBooking = bookingRepository.save(booking);

            List<BookingSeat> bookingSeats = availableSeats.stream()
                    .map(showSeat -> BookingSeat.builder()
                            .booking(savedBooking)
                            .showSeat(showSeat)
                            .price(showSeat.getPrice())
                            .build())
                    .toList();
            bookingSeatRepository.saveAll(bookingSeats);

            availableSeats.forEach(seat -> seat.setBookingId(savedBooking.getId()));
            showSeatRepository.saveAll(availableSeats);

            showRepository.decrementAvailableSeats(request.getShowId(), request.getSeatIds().size());

            // Transactional outbox: the event row commits atomically with the booking;
            // OutboxPoller relays it to Kafka afterwards (no dual-write).
            outboxService.record(new BookingInitiatedEvent(savedBooking));

            bookingMetrics.recordInitiated();
            return toInitiateResponse(savedBooking, availableSeats, now);
        } catch (RuntimeException e) {
            // Transaction rolls back automatically; free the Redis locks so seats reopen now.
            seatLockService.releaseLocks(request.getShowId(), request.getSeatIds());
            throw e;
        }
    }

    /**
     * Confirm flow (LLD 3.2): validate the booking is INITIATED and unexpired, charge via
     * PaymentService, then mark the booking CONFIRMED and its seats BOOKED.
     *
     * <p>SAGA COMPENSATION: payment and booking-confirmation are separate steps. If the
     * charge succeeds but the confirmation write fails, we refund conceptually by marking the
     * payment for reversal and rethrow — the booking stays INITIATED and will expire, freeing
     * seats. (A full saga would enqueue a refund command; here we log and surface the error.)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingConfirmResponse confirmBooking(Long bookingId, BookingConfirmRequest request) {
        // Time the whole money path so Grafana can chart p95/p99 confirm latency (RED "Duration").
        return bookingMetrics.timeConfirm(() -> doConfirmBooking(bookingId, request));
    }

    private BookingConfirmResponse doConfirmBooking(Long bookingId, BookingConfirmRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        assertTransition(booking.getBookingStatus(), BookingStatus.CONFIRMED);
        if (booking.getExpiresAt() != null && Instant.now().isAfter(booking.getExpiresAt())) {
            throw new BookingExpiredException(bookingId);
        }

        PaymentRequest paymentRequest = PaymentRequest.builder()
                .bookingId(bookingId)
                .amount(booking.getTotalAmount())
                .paymentMethod(request.getPaymentMethod())
                .gatewayType(GatewayType.STRIPE)
                .paymentDetails(request.getPaymentDetails())
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        Payment payment = paymentService.processPayment(paymentRequest);

        try {
            Instant now = Instant.now();
            booking.setBookingStatus(BookingStatus.CONFIRMED);
            booking.setPaymentStatus(PaymentStatus.COMPLETED);
            booking.setPaymentId(String.valueOf(payment.getId()));
            booking.setPaymentMethod(request.getPaymentMethod());
            booking.setConfirmedAt(now);
            bookingRepository.save(booking);

            List<ShowSeat> seats = showSeatRepository.findByShowId(booking.getShow().getId()).stream()
                    .filter(s -> bookingId.equals(s.getBookingId()))
                    .toList();
            seats.forEach(s -> s.setStatus(ShowSeatStatus.BOOKED));
            showSeatRepository.saveAll(seats);

            outboxService.record(new BookingConfirmedEvent(booking));
            bookingMetrics.recordConfirmed();
            return toConfirmResponse(booking, seats);
        } catch (RuntimeException e) {
            log.error("Payment {} succeeded but confirmation failed for booking {}; "
                    + "booking left INITIATED to expire (compensation)", payment.getId(), bookingId, e);
            throw e;
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancelBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (booking.getUser() == null || !booking.getUser().getId().equals(userId)) {
            throw new InvalidRequestException("Booking does not belong to user " + userId);
        }
        assertTransition(booking.getBookingStatus(), BookingStatus.CANCELLED);

        Instant now = Instant.now();
        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(now);
        bookingRepository.save(booking);

        List<ShowSeat> seats = showSeatRepository.findByShowId(booking.getShow().getId()).stream()
                .filter(s -> bookingId.equals(s.getBookingId()))
                .toList();
        seats.forEach(s -> {
            s.setStatus(ShowSeatStatus.AVAILABLE);
            s.setLockedAt(null);
            s.setLockedBy(null);
            s.setBookingId(null);
        });
        showSeatRepository.saveAll(seats);
        showRepository.incrementAvailableSeats(booking.getShow().getId(), seats.size());

        List<Long> seatIds = seats.stream().map(ShowSeat::getId).toList();
        seatLockService.releaseLocks(booking.getShow().getId(), seatIds);
        outboxService.record(new BookingCancelledEvent(booking));
        bookingMetrics.recordCancelled();
    }

    @Transactional(readOnly = true)
    public BookingDetailsResponse getBookingDetails(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (booking.getUser() == null || !booking.getUser().getId().equals(userId)) {
            throw new InvalidRequestException("Booking does not belong to user " + userId);
        }
        List<ShowSeat> seats = showSeatRepository.findByShowId(booking.getShow().getId()).stream()
                .filter(s -> bookingId.equals(s.getBookingId()))
                .toList();
        return BookingDetailsResponse.builder()
                .bookingId(booking.getId())
                .bookingReference(booking.getBookingReference())
                .bookingStatus(booking.getBookingStatus().name())
                .paymentStatus(booking.getPaymentStatus() != null
                        ? booking.getPaymentStatus().name() : null)
                .showDetails(toShowDetails(booking.getShow()))
                .seats(seats.stream().map(this::toSeatDto).toList())
                .totalAmount(booking.getTotalAmount())
                .bookedAt(booking.getBookedAt())
                .expiresAt(booking.getExpiresAt())
                .confirmedAt(booking.getConfirmedAt())
                .cancelledAt(booking.getCancelledAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<BookingDetailsResponse> getUserBookings(Long userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(b -> getBookingDetails(b.getId(), userId))
                .toList();
    }

    /**
     * Enforces the booking state machine (LLD section 4). Rejects illegal transitions with
     * a clear error instead of silently corrupting state.
     */
    private void assertTransition(BookingStatus from, BookingStatus to) {
        boolean allowed = switch (to) {
            case CONFIRMED, CANCELLED, EXPIRED -> from == BookingStatus.INITIATED
                    || (to == BookingStatus.CANCELLED && from == BookingStatus.CONFIRMED);
            case REFUNDED -> from == BookingStatus.CONFIRMED || from == BookingStatus.CANCELLED;
            default -> false;
        };
        if (!allowed) {
            throw new InvalidRequestException(
                    "Illegal booking transition: " + from + " -> " + to);
        }
    }

    private BookingInitiateResponse toInitiateResponse(Booking booking, List<ShowSeat> seats, Instant now) {
        return BookingInitiateResponse.builder()
                .bookingId(booking.getId())
                .bookingReference(booking.getBookingReference())
                .showDetails(toShowDetails(booking.getShow()))
                .seats(seats.stream().map(this::toSeatDto).toList())
                .totalAmount(booking.getTotalAmount())
                .expiresAt(booking.getExpiresAt())
                .expiresInSeconds(booking.getExpiresAt() != null
                        ? Math.max(0, ChronoUnit.SECONDS.between(now, booking.getExpiresAt())) : 0)
                .build();
    }

    private BookingConfirmResponse toConfirmResponse(Booking booking, List<ShowSeat> seats) {
        return BookingConfirmResponse.builder()
                .bookingId(booking.getId())
                .bookingReference(booking.getBookingReference())
                .bookingStatus(booking.getBookingStatus().name())
                .paymentStatus(booking.getPaymentStatus().name())
                .paymentId(booking.getPaymentId())
                .confirmedAt(booking.getConfirmedAt())
                .ticketUrl("https://bms.com/tickets/" + booking.getBookingReference())
                .showDetails(toShowDetails(booking.getShow()))
                .seats(seats.stream().map(this::toSeatDto).toList())
                .totalAmount(booking.getTotalAmount())
                .build();
    }

    private ShowDetailsDTO toShowDetails(Show s) {
        if (s == null) {
            return null;
        }
        return ShowDetailsDTO.builder()
                .showId(s.getId())
                .movieName(s.getMovie() != null ? s.getMovie().getTitle() : null)
                .theaterName(s.getScreen() != null && s.getScreen().getTheater() != null
                        ? s.getScreen().getTheater().getName() : null)
                .screenName(s.getScreen() != null ? s.getScreen().getName() : null)
                .showDate(s.getShowDate())
                .showTime(s.getShowTime() != null ? s.getShowTime().toString() : null)
                .build();
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
