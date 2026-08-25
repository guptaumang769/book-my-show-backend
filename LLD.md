# BookMyShow - Low Level Design (LLD)

## 📋 Table of Contents
1. [Component Architecture](#component-architecture)
2. [Class Diagrams](#class-diagrams)
3. [Sequence Diagrams](#sequence-diagrams)
4. [State Machines](#state-machines)
5. [Design Patterns Used](#design-patterns-used)
6. [Detailed API Specifications](#detailed-api-specifications)
7. [Database Transactions](#database-transactions)
8. [Error Handling](#error-handling)
9. [Code Structure](#code-structure)

---

## 1. Component Architecture

### Service Layer Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     Controller Layer                     │
│  (REST endpoints, request validation, response mapping)  │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                     Service Layer                        │
│    (Business logic, orchestration, transactions)         │
│  ┌──────────────┬───────────────┬──────────────┐        │
│  │ BookingService│CatalogService│PaymentService│        │
│  └──────────────┴───────────────┴──────────────┘        │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                  Repository Layer                        │
│     (Data access, JPA repositories, queries)             │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                   Database Layer                         │
│         (PostgreSQL, Redis, Elasticsearch)               │
└──────────────────────────────────────────────────────────┘
```

---

## 2. Class Diagrams

### 2.1 Domain Models

#### User Domain
```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String passwordHash;
    
    private String firstName;
    private String lastName;
    private String phone;
    
    @OneToMany(mappedBy = "user")
    private List<Booking> bookings;
    
    private Instant createdAt;
    private Instant updatedAt;
    
    // Getters, setters, builders
}
```

#### Movie Domain
```java
@Entity
@Table(name = "movies")
public class Movie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    private Integer durationMinutes;
    private String genre;
    private String language;
    private LocalDate releaseDate;
    private String rating; // PG, PG-13, R
    private String posterUrl;
    private String trailerUrl;
    
    @Column(nullable = false)
    private Boolean isActive = true;
    
    @OneToMany(mappedBy = "movie")
    private List<Show> shows;
    
    private Instant createdAt;
    private Instant updatedAt;
}
```

#### Theater & Screen Domain
```java
@Entity
@Table(name = "theaters")
public class Theater {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @ManyToOne
    @JoinColumn(name = "city_id")
    private City city;
    
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    
    @OneToMany(mappedBy = "theater", cascade = CascadeType.ALL)
    private List<Screen> screens;
    
    private Integer totalScreens;
    private Instant createdAt;
}

@Entity
@Table(name = "screens")
public class Screen {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "theater_id")
    private Theater theater;
    
    private String name; // "Screen 1", "IMAX", etc.
    private Integer totalSeats;
    
    @OneToMany(mappedBy = "screen", cascade = CascadeType.ALL)
    private List<Seat> seats;
    
    private Instant createdAt;
}
```

#### Seat Domain
```java
@Entity
@Table(name = "seats")
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "screen_id")
    private Screen screen;
    
    private String rowNum; // A, B, C
    private Integer seatNumber; // 1, 2, 3
    
    @Enumerated(EnumType.STRING)
    private SeatType seatType; // REGULAR, PREMIUM, VIP
    
    private Instant createdAt;
}

enum SeatType {
    REGULAR(0),
    PREMIUM(50),
    VIP(100);
    
    private final int priceIncrement;
    
    SeatType(int priceIncrement) {
        this.priceIncrement = priceIncrement;
    }
    
    public int getPriceIncrement() {
        return priceIncrement;
    }
}
```

#### Show Domain (Critical)
```java
@Entity
@Table(name = "shows")
public class Show {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "movie_id")
    private Movie movie;
    
    @ManyToOne
    @JoinColumn(name = "screen_id")
    private Screen screen;
    
    private LocalDate showDate;
    private LocalTime showTime;
    private LocalTime endTime;
    
    private BigDecimal basePrice;
    private Integer availableSeats; // Denormalized for quick check
    private Integer totalSeats;
    
    @Enumerated(EnumType.STRING)
    private ShowStatus status;
    
    @OneToMany(mappedBy = "show", cascade = CascadeType.ALL)
    private List<ShowSeat> showSeats;
    
    private Instant createdAt;
    private Instant updatedAt;
}

enum ShowStatus {
    ACTIVE,
    CANCELLED,
    HOUSEFULL,
    COMPLETED
}
```

#### ShowSeat Domain (Junction with State)
```java
@Entity
@Table(name = "show_seats")
public class ShowSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "show_id")
    private Show show;
    
    @ManyToOne
    @JoinColumn(name = "seat_id")
    private Seat seat;
    
    private BigDecimal price;
    
    @Enumerated(EnumType.STRING)
    private ShowSeatStatus status;
    
    private Instant lockedAt;
    
    @ManyToOne
    @JoinColumn(name = "locked_by")
    private User lockedBy;
    
    private Long bookingId;
    
    @Version // Optimistic locking
    private Long version;
    
    private Instant createdAt;
    private Instant updatedAt;
}

enum ShowSeatStatus {
    AVAILABLE,
    LOCKED,
    BOOKED
}
```

#### Booking Domain (Critical)
```java
@Entity
@Table(name = "bookings")
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    
    @ManyToOne
    @JoinColumn(name = "show_id")
    private Show show;
    
    @Column(unique = true, nullable = false)
    private String bookingReference;
    
    private BigDecimal totalAmount;
    
    @Enumerated(EnumType.STRING)
    private BookingStatus bookingStatus;
    
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;
    
    private String paymentId;
    private String paymentMethod;
    
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    private List<BookingSeat> bookingSeats;
    
    private Instant bookedAt;
    private Instant expiresAt; // 10 minutes from creation
    private Instant confirmedAt;
    private Instant cancelledAt;
    
    private Instant createdAt;
    private Instant updatedAt;
}

enum BookingStatus {
    INITIATED,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    REFUNDED
}

enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED
}
```

#### Payment Domain
```java
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "booking_id")
    private Booking booking;
    
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;
    
    private String paymentGateway;
    private String gatewayTransactionId;
    
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> paymentDetails;
    
    private Instant createdAt;
    private Instant updatedAt;
}

enum PaymentMethod {
    CREDIT_CARD,
    DEBIT_CARD,
    UPI,
    NET_BANKING,
    WALLET
}
```

### 2.2 Service Classes

#### BookingService
```java
@Service
@Transactional
public class BookingService {
    
    private final BookingRepository bookingRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final SeatLockService seatLockService;
    private final PaymentService paymentService;
    private final EventPublisher eventPublisher;
    
    public BookingInitiateResponse initiateBooking(BookingInitiateRequest request) {
        // Implementation in sequence diagram section
    }
    
    public BookingConfirmResponse confirmBooking(Long bookingId, PaymentRequest payment) {
        // Implementation in sequence diagram section
    }
    
    public void cancelBooking(Long bookingId, Long userId) {
        // Implementation in sequence diagram section
    }
    
    public BookingDetailsResponse getBookingDetails(Long bookingId, Long userId) {
        // Implementation in sequence diagram section
    }
    
    private void validateSeatAvailability(Long showId, List<Long> seatIds) {
        // Validation logic
    }
    
    private String generateBookingReference() {
        return "BMS" + System.currentTimeMillis() + RandomStringUtils.randomAlphanumeric(4);
    }
}
```

#### SeatLockService
```java
@Service
public class SeatLockService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);
    
    public boolean acquireLock(Long showId, Long seatId, Long userId) {
        String lockKey = buildLockKey(showId, seatId);
        String lockValue = buildLockValue(userId);
        
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, lockValue, LOCK_DURATION);
        
        return Boolean.TRUE.equals(acquired);
    }
    
    public boolean acquireLocks(Long showId, List<Long> seatIds, Long userId) {
        List<String> acquiredLocks = new ArrayList<>();
        
        try {
            for (Long seatId : seatIds) {
                boolean acquired = acquireLock(showId, seatId, userId);
                if (!acquired) {
                    // Rollback all acquired locks
                    releaseLocks(acquiredLocks);
                    return false;
                }
                acquiredLocks.add(buildLockKey(showId, seatId));
            }
            return true;
        } catch (Exception e) {
            releaseLocks(acquiredLocks);
            throw e;
        }
    }
    
    public void releaseLock(Long showId, Long seatId) {
        String lockKey = buildLockKey(showId, seatId);
        redisTemplate.delete(lockKey);
    }
    
    public void releaseLocks(List<String> lockKeys) {
        if (!lockKeys.isEmpty()) {
            redisTemplate.delete(lockKeys);
        }
    }
    
    private String buildLockKey(Long showId, Long seatId) {
        return "seat:lock:" + showId + ":" + seatId;
    }
    
    private String buildLockValue(Long userId) {
        return userId + ":" + System.currentTimeMillis();
    }
}
```

#### CatalogService
```java
@Service
@Transactional(readOnly = true)
public class CatalogService {
    
    private final MovieRepository movieRepository;
    private final ShowRepository showRepository;
    private final TheaterRepository theaterRepository;
    private final CacheManager cacheManager;
    private final ElasticsearchOperations elasticsearchOperations;
    
    @Cacheable(value = "movies", key = "#cityId + '-' + #genre")
    public List<MovieDTO> getMoviesByCity(Long cityId, String genre, String language) {
        // Fetch from DB or cache
    }
    
    @Cacheable(value = "shows", key = "#movieId + '-' + #cityId + '-' + #date", 
               unless = "#result.isEmpty()")
    public List<ShowDTO> getShowsForMovie(Long movieId, Long cityId, LocalDate date) {
        return showRepository.findShows(movieId, cityId, date)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    @Cacheable(value = "show_seats", key = "#showId", unless = "#result == null")
    public ShowSeatsResponse getAvailableSeats(Long showId) {
        Show show = showRepository.findById(showId)
            .orElseThrow(() -> new ShowNotFoundException(showId));
        
        List<ShowSeat> availableSeats = showSeatRepository
            .findByShowIdAndStatus(showId, ShowSeatStatus.AVAILABLE);
        
        return buildSeatsResponse(show, availableSeats);
    }
    
    public List<MovieDTO> searchMovies(String query, SearchFilters filters) {
        // Use Elasticsearch for search
        return elasticsearchOperations.search(/* ... */);
    }
}
```

#### PaymentService
```java
@Service
public class PaymentService {
    
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayFactory gatewayFactory;
    private final BookingRepository bookingRepository;
    
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        // Create payment record
        Payment payment = new Payment();
        payment.setBooking(bookingRepository.getReferenceById(request.getBookingId()));
        payment.setAmount(request.getAmount());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setStatus(PaymentStatus.INITIATED);
        paymentRepository.save(payment);
        
        try {
            // Call payment gateway
            PaymentGateway gateway = gatewayFactory.getGateway(request.getGatewayType());
            GatewayResponse gatewayResponse = gateway.processPayment(request);
            
            if (gatewayResponse.isSuccess()) {
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setGatewayTransactionId(gatewayResponse.getTransactionId());
                paymentRepository.save(payment);
                
                return PaymentResponse.success(payment.getId(), gatewayResponse);
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                
                return PaymentResponse.failure(payment.getId(), gatewayResponse.getError());
            }
        } catch (Exception e) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw new PaymentProcessingException("Payment failed", e);
        }
    }
    
    @Transactional
    public void handlePaymentWebhook(PaymentWebhookDTO webhook) {
        // Handle async payment confirmation from gateway
        Payment payment = paymentRepository
            .findByGatewayTransactionId(webhook.getTransactionId())
            .orElseThrow(() -> new PaymentNotFoundException());
        
        if (webhook.getStatus().equals("SUCCESS")) {
            payment.setStatus(PaymentStatus.SUCCESS);
            paymentRepository.save(payment);
            
            // Trigger booking confirmation
            eventPublisher.publish(new PaymentSuccessEvent(payment.getBooking().getId()));
        }
    }
}
```

---

## 3. Sequence Diagrams

### 3.1 Initiate Booking Flow

```
User        Controller    BookingService    SeatLockService    ShowSeatRepo    Redis    DB

 │               │              │                  │                │           │      │
 │──POST /bookings/initiate──>  │                  │                │           │      │
 │               │              │                  │                │           │      │
 │               │──initiateBooking()───>          │                │           │      │
 │               │              │                  │                │           │      │
 │               │              │──acquireLocks(showId, seatIds, userId)───>    │      │
 │               │              │                  │                │           │      │
 │               │              │                  │─SETNX seat:lock:*:*──>     │      │
 │               │              │                  │<──OK/FAIL──────────────────│      │
 │               │              │<─true/false──────│                │           │      │
 │               │              │                  │                │           │      │
 │               │              │[if locks acquired]                │           │      │
 │               │              │                  │                │           │      │
 │               │              │────────START TRANSACTION──────────────────────────────>│
 │               │              │                  │                │           │      │
 │               │              │─findByShowIdAndSeatIdsAndStatus───>           │      │
 │               │              │<─List<ShowSeat>───────────────────│           │      │
 │               │              │                  │                │           │      │
 │               │              │[validate availability]            │           │      │
 │               │              │                  │                │           │      │
 │               │              │─updateStatus(LOCKED)──────────────>           │      │
 │               │              │                  │                │           │      │
 │               │              │─createBooking()──────────────────────────────────────>│
 │               │              │<─Booking─────────────────────────────────────────────│
 │               │              │                  │                │           │      │
 │               │              │─decrementAvailableSeats()────────────────────────────>│
 │               │              │                  │                │           │      │
 │               │              │─────────COMMIT TRANSACTION────────────────────────────>│
 │               │              │                  │                │           │      │
 │               │<─BookingInitiateResponse─────────│                │           │      │
 │<────200 OK────│              │                  │                │           │      │
 │               │              │                  │                │           │      │
 │               │              │[if locks failed]                  │           │      │
 │<────409 Conflict─────────────│                  │                │           │      │
```

### 3.2 Confirm Booking Flow (After Payment)

```
User      Controller    BookingService    PaymentService    ShowSeatRepo    EventBus    DB

 │              │              │                │                │            │         │
 │─PUT /bookings/{id}/confirm─>│                │                │            │         │
 │              │              │                │                │            │         │
 │              │─confirmBooking()───>          │                │            │         │
 │              │              │                │                │            │         │
 │              │              │─getBooking(id)──────────────────────────────────────────>│
 │              │              │<─Booking────────────────────────────────────────────────│
 │              │              │                │                │            │         │
 │              │              │[validate: status=INITIATED, not expired]    │         │
 │              │              │                │                │            │         │
 │              │              │─processPayment()──>            │            │         │
 │              │              │                │─Call Gateway──>            │         │
 │              │              │                │<─Success───────            │         │
 │              │              │<─PaymentResponse───│            │            │         │
 │              │              │                │                │            │         │
 │              │              │[if payment success]             │            │         │
 │              │              │────────START TRANSACTION────────────────────────────────>│
 │              │              │                │                │            │         │
 │              │              │─updateBookingStatus(CONFIRMED)──────────────────────────>│
 │              │              │                │                │            │         │
 │              │              │─updatePaymentInfo()─────────────────────────────────────>│
 │              │              │                │                │            │         │
 │              │              │─updateShowSeatsStatus(BOOKED)───>            │         │
 │              │              │                │                │            │         │
 │              │              │──────────COMMIT TRANSACTION─────────────────────────────>│
 │              │              │                │                │            │         │
 │              │              │─publishEvent(BookingConfirmed)──────────────>         │
 │              │              │                │                │            │         │
 │              │<─BookingConfirmResponse──────│                │            │         │
 │<───200 OK────│              │                │                │            │         │
```

### 3.3 Expire Stale Bookings (Background Job)

```
Scheduler    BookingService    BookingRepo    ShowSeatRepo    SeatLockService    Redis    DB

    │               │              │               │                  │            │      │
    │─@Scheduled────>│              │               │                  │            │      │
    │               │              │               │                  │            │      │
    │               │─expireStaleBookings()        │                  │            │      │
    │               │              │               │                  │            │      │
    │               │─findExpiredBookings()──>     │                  │            │      │
    │               │<─List<Booking>───────────────│                  │            │      │
    │               │              │               │                  │            │      │
    │               │[for each booking]            │                  │            │      │
    │               │              │               │                  │            │      │
    │               │───────START TRANSACTION──────────────────────────────────────────────>│
    │               │              │               │                  │            │      │
    │               │─updateStatus(EXPIRED)────────────────────────────────────────────────>│
    │               │              │               │                  │            │      │
    │               │─getBookingSeats()────────────────────────────────────────────────────>│
    │               │<─List<BookingSeat>───────────────────────────────────────────────────│
    │               │              │               │                  │            │      │
    │               │─updateShowSeatsStatus(AVAILABLE)───>            │            │      │
    │               │              │               │                  │            │      │
    │               │─incrementAvailableSeats()────────────────────────────────────────────>│
    │               │              │               │                  │            │      │
    │               │────────COMMIT TRANSACTION────────────────────────────────────────────>│
    │               │              │               │                  │            │      │
    │               │─releaseLocks(showId, seatIds)───────────────────>            │      │
    │               │              │               │                  │─DEL locks──>      │
    │               │              │               │                  │            │      │
    │               │─publishEvent(BookingExpired)──>                 │            │      │
```

---

## 4. State Machines

### 4.1 Booking State Machine

```
                     initiateBooking()
    ┌─────────────────────────────────────────┐
    │                                         │
    │                                         ▼
    │                                  ┌──────────────┐
    │                                  │   INITIATED  │
    │                                  └──────┬───────┘
    │                                         │
    │                    ┌────────────────────┼────────────────────┐
    │                    │                    │                    │
    │              confirmBooking()      expireJob()         cancelBooking()
    │                    │                    │                    │
    │                    ▼                    ▼                    ▼
    │             ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
    │             │  CONFIRMED   │     │   EXPIRED    │     │  CANCELLED   │
    │             └──────┬───────┘     └──────────────┘     └──────────────┘
    │                    │
    │            requestRefund()
    │                    │
    │                    ▼
    │             ┌──────────────┐
    └─────────────│   REFUNDED   │
                  └──────────────┘


State Transition Rules:
- INITIATED → CONFIRMED: Payment successful, within expiry time
- INITIATED → EXPIRED: Expiry time passed, payment not completed
- INITIATED → CANCELLED: User cancels before payment
- CONFIRMED → CANCELLED: User cancels after payment (with conditions)
- CONFIRMED → REFUNDED: Refund processed for cancelled booking
```

### 4.2 ShowSeat State Machine

```
                 showCreated()
                      │
                      ▼
               ┌──────────────┐
               │  AVAILABLE   │◄──────────────────┐
               └──────┬───────┘                   │
                      │                           │
              initiateBooking()          expireBooking() / cancelBooking()
                      │                           │
                      ▼                           │
               ┌──────────────┐                   │
               │    LOCKED    │───────────────────┘
               └──────┬───────┘
                      │
              confirmBooking()
                      │
                      ▼
               ┌──────────────┐
               │    BOOKED    │
               └──────────────┘


State Transition Rules:
- AVAILABLE → LOCKED: User initiates booking, lock acquired
- LOCKED → AVAILABLE: Booking expired or cancelled
- LOCKED → BOOKED: Payment successful, booking confirmed
- BOOKED: Final state (no transitions out)
```

---

## 5. Design Patterns Used

### 5.1 Strategy Pattern - Payment Gateway Selection

```java
public interface PaymentGateway {
    GatewayResponse processPayment(PaymentRequest request);
    RefundResponse processRefund(RefundRequest request);
    PaymentStatus getPaymentStatus(String transactionId);
}

@Component
public class StripePaymentGateway implements PaymentGateway {
    @Override
    public GatewayResponse processPayment(PaymentRequest request) {
        // Stripe-specific implementation
    }
}

@Component
public class RazorpayPaymentGateway implements PaymentGateway {
    @Override
    public GatewayResponse processPayment(PaymentRequest request) {
        // Razorpay-specific implementation
    }
}

@Component
public class PaymentGatewayFactory {
    private final Map<GatewayType, PaymentGateway> gateways;
    
    public PaymentGatewayFactory(List<PaymentGateway> gatewayList) {
        // Initialize map
    }
    
    public PaymentGateway getGateway(GatewayType type) {
        return gateways.get(type);
    }
}
```

### 5.2 Repository Pattern - Data Access Abstraction

```java
@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    List<Booking> findByBookingStatusAndExpiresAtBefore(
        BookingStatus status, Instant expiryTime);
    
    Optional<Booking> findByBookingReference(String bookingReference);
    
    @Query("SELECT b FROM Booking b WHERE b.show.id = :showId " +
           "AND b.bookingStatus IN :statuses")
    List<Booking> findByShowIdAndStatusIn(
        @Param("showId") Long showId, 
        @Param("statuses") List<BookingStatus> statuses);
}
```

### 5.3 Builder Pattern - Complex Object Construction

```java
@Builder
public class BookingInitiateRequest {
    private Long userId;
    private Long showId;
    private List<Long> seatIds;
    
    // Validation
    public void validate() {
        if (userId == null || showId == null || seatIds == null || seatIds.isEmpty()) {
            throw new InvalidRequestException("Required fields missing");
        }
        if (seatIds.size() > 10) {
            throw new InvalidRequestException("Maximum 10 seats per booking");
        }
    }
}

// Usage
BookingInitiateRequest request = BookingInitiateRequest.builder()
    .userId(123L)
    .showId(456L)
    .seatIds(Arrays.asList(1L, 2L, 3L))
    .build();
request.validate();
```

### 5.4 Factory Pattern - Event Creation

```java
public interface BookingEvent {
    String getEventType();
    Long getBookingId();
    Instant getTimestamp();
}

public class BookingEventFactory {
    public static BookingEvent createEvent(BookingStatus status, Booking booking) {
        return switch (status) {
            case CONFIRMED -> new BookingConfirmedEvent(booking);
            case CANCELLED -> new BookingCancelledEvent(booking);
            case EXPIRED -> new BookingExpiredEvent(booking);
            default -> throw new IllegalArgumentException("Unsupported status");
        };
    }
}
```

### 5.5 Template Method Pattern - Booking Workflow

```java
public abstract class BookingWorkflow {
    
    public final BookingResponse executeBooking(BookingRequest request) {
        validateRequest(request);
        
        if (!checkAvailability(request)) {
            return BookingResponse.failure("Seats not available");
        }
        
        boolean locksAcquired = acquireLocks(request);
        if (!locksAcquired) {
            return BookingResponse.failure("Could not acquire locks");
        }
        
        try {
            BookingResponse response = processBooking(request);
            postProcessing(response);
            return response;
        } catch (Exception e) {
            handleFailure(request);
            throw e;
        }
    }
    
    protected abstract void validateRequest(BookingRequest request);
    protected abstract boolean checkAvailability(BookingRequest request);
    protected abstract boolean acquireLocks(BookingRequest request);
    protected abstract BookingResponse processBooking(BookingRequest request);
    protected abstract void postProcessing(BookingResponse response);
    protected abstract void handleFailure(BookingRequest request);
}
```

### 5.6 Observer Pattern - Event Publishing

```java
@Component
public class BookingEventPublisher {
    private final KafkaTemplate<String, BookingEvent> kafkaTemplate;
    private final List<BookingEventListener> listeners = new ArrayList<>();
    
    public void publish(BookingEvent event) {
        // Publish to Kafka
        kafkaTemplate.send("booking-events", event.getEventType(), event);
        
        // Notify local listeners
        listeners.forEach(listener -> listener.onEvent(event));
    }
    
    public void subscribe(BookingEventListener listener) {
        listeners.add(listener);
    }
}

public interface BookingEventListener {
    void onEvent(BookingEvent event);
}

@Component
public class NotificationListener implements BookingEventListener {
    @Override
    public void onEvent(BookingEvent event) {
        if (event instanceof BookingConfirmedEvent) {
            sendConfirmationEmail((BookingConfirmedEvent) event);
        }
    }
}
```

---

## 6. Detailed API Specifications

### 6.1 POST /api/v1/bookings/initiate

**Request**:
```json
{
  "userId": 12345,
  "showId": 789,
  "seatIds": [101, 102, 103]
}
```

**Response (Success - 200)**:
```json
{
  "success": true,
  "data": {
    "bookingId": 9876,
    "bookingReference": "BMS1737288000ABCD",
    "showDetails": {
      "showId": 789,
      "movieName": "Avengers: Endgame",
      "theaterName": "PVR Phoenix",
      "screenName": "IMAX",
      "showDate": "2026-01-25",
      "showTime": "19:30"
    },
    "seats": [
      {"seatId": 101, "row": "F", "number": 10, "type": "REGULAR", "price": 250.00},
      {"seatId": 102, "row": "F", "number": 11, "type": "REGULAR", "price": 250.00},
      {"seatId": 103, "row": "F", "number": 12, "type": "REGULAR", "price": 250.00}
    ],
    "totalAmount": 750.00,
    "expiresAt": "2026-01-19T15:45:00Z",
    "expiresInSeconds": 600
  }
}
```

**Response (Failure - 409 Conflict)**:
```json
{
  "success": false,
  "error": {
    "code": "SEATS_NOT_AVAILABLE",
    "message": "One or more selected seats are no longer available",
    "unavailableSeats": [101, 102]
  }
}
```

**Error Codes**:
- `400` - Invalid request (missing fields, invalid IDs)
- `404` - Show not found
- `409` - Seats not available / already locked
- `429` - Rate limit exceeded
- `500` - Internal server error

---

### 6.2 PUT /api/v1/bookings/{bookingId}/confirm

**Request**:
```json
{
  "paymentMethod": "UPI",
  "paymentDetails": {
    "upiId": "user@paytm",
    "transactionId": "TXN123456"
  }
}
```

**Response (Success - 200)**:
```json
{
  "success": true,
  "data": {
    "bookingId": 9876,
    "bookingReference": "BMS1737288000ABCD",
    "bookingStatus": "CONFIRMED",
    "paymentStatus": "COMPLETED",
    "paymentId": "PAY987654",
    "confirmedAt": "2026-01-19T15:37:00Z",
    "ticketUrl": "https://bms.com/tickets/BMS1737288000ABCD",
    "qrCode": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...",
    "showDetails": { /* same as initiate response */ },
    "seats": [ /* same as initiate response */ ],
    "totalAmount": 750.00
  }
}
```

**Response (Failure - 400)**:
```json
{
  "success": false,
  "error": {
    "code": "BOOKING_EXPIRED",
    "message": "Booking has expired. Please book again."
  }
}
```

**Error Codes**:
- `400` - Booking expired / already confirmed / cancelled
- `402` - Payment failed
- `404` - Booking not found
- `500` - Internal server error

---

## 7. Database Transactions

### 7.1 Initiate Booking Transaction

```java
@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
public BookingInitiateResponse initiateBooking(BookingInitiateRequest request) {
    
    // 1. Acquire distributed locks (outside transaction)
    boolean locksAcquired = seatLockService.acquireLocks(
        request.getShowId(), request.getSeatIds(), request.getUserId());
    
    if (!locksAcquired) {
        throw new SeatsNotAvailableException("Seats already selected");
    }
    
    try {
        // 2. Start database transaction (implicit with @Transactional)
        
        // 3. Fetch and lock show seats rows
        List<ShowSeat> showSeats = showSeatRepository
            .findByShowIdAndSeatIdInForUpdate(
                request.getShowId(), 
                request.getSeatIds()
            );
        
        // 4. Validate availability
        List<ShowSeat> availableSeats = showSeats.stream()
            .filter(seat -> seat.getStatus() == ShowSeatStatus.AVAILABLE)
            .collect(Collectors.toList());
        
        if (availableSeats.size() != request.getSeatIds().size()) {
            throw new SeatsNotAvailableException("Some seats are not available");
        }
        
        // 5. Calculate total amount
        BigDecimal totalAmount = availableSeats.stream()
            .map(ShowSeat::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 6. Update seat status to LOCKED
        Instant now = Instant.now();
        availableSeats.forEach(seat -> {
            seat.setStatus(ShowSeatStatus.LOCKED);
            seat.setLockedAt(now);
            seat.setLockedBy(userRepository.getReferenceById(request.getUserId()));
            seat.setUpdatedAt(now);
        });
        showSeatRepository.saveAll(availableSeats);
        
        // 7. Create booking record
        Booking booking = Booking.builder()
            .user(userRepository.getReferenceById(request.getUserId()))
            .show(showRepository.getReferenceById(request.getShowId()))
            .bookingReference(generateBookingReference())
            .totalAmount(totalAmount)
            .bookingStatus(BookingStatus.INITIATED)
            .paymentStatus(PaymentStatus.PENDING)
            .bookedAt(now)
            .expiresAt(now.plus(10, ChronoUnit.MINUTES))
            .createdAt(now)
            .build();
        booking = bookingRepository.save(booking);
        
        // 8. Create booking_seats junction records
        List<BookingSeat> bookingSeats = availableSeats.stream()
            .map(showSeat -> BookingSeat.builder()
                .booking(booking)
                .showSeat(showSeat)
                .price(showSeat.getPrice())
                .build())
            .collect(Collectors.toList());
        bookingSeatRepository.saveAll(bookingSeats);
        
        // 9. Update booking_id in show_seats
        availableSeats.forEach(seat -> seat.setBookingId(booking.getId()));
        showSeatRepository.saveAll(availableSeats);
        
        // 10. Decrement available seats count in show
        showRepository.decrementAvailableSeats(request.getShowId(), request.getSeatIds().size());
        
        // 11. Transaction commits here (implicit)
        
        // 12. Publish event (after commit)
        eventPublisher.publish(new BookingInitiatedEvent(booking));
        
        return toInitiateResponse(booking, availableSeats);
        
    } catch (Exception e) {
        // Transaction rollback happens automatically
        // Release Redis locks
        seatLockService.releaseLocks(request.getShowId(), request.getSeatIds());
        throw e;
    }
}
```

### 7.2 Transaction Isolation Levels

- **Initiate Booking**: `READ_COMMITTED` - Prevent dirty reads while allowing concurrent bookings for different shows
- **Confirm Booking**: `READ_COMMITTED` - Standard isolation
- **Expire Bookings**: `READ_COMMITTED` - Background job, no higher isolation needed

---

## 8. Error Handling

### 8.1 Exception Hierarchy

```java
public class BookingException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;
    
    public BookingException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}

public class SeatsNotAvailableException extends BookingException {
    public SeatsNotAvailableException(String message) {
        super(message, "SEATS_NOT_AVAILABLE", HttpStatus.CONFLICT);
    }
}

public class BookingExpiredException extends BookingException {
    public BookingExpiredException(String bookingId) {
        super("Booking " + bookingId + " has expired", 
              "BOOKING_EXPIRED", HttpStatus.BAD_REQUEST);
    }
}

public class PaymentFailedException extends BookingException {
    public PaymentFailedException(String message) {
        super(message, "PAYMENT_FAILED", HttpStatus.PAYMENT_REQUIRED);
    }
}
```

### 8.2 Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BookingException.class)
    public ResponseEntity<ErrorResponse> handleBookingException(BookingException ex) {
        ErrorResponse error = ErrorResponse.builder()
            .success(false)
            .error(ErrorDetails.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build())
            .build();
        
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {
        
        List<String> errors = ex.getBindingResult().getFieldErrors()
            .stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.toList());
        
        ErrorResponse error = ErrorResponse.builder()
            .success(false)
            .error(ErrorDetails.builder()
                .code("VALIDATION_ERROR")
                .message("Invalid request parameters")
                .details(errors)
                .timestamp(Instant.now())
                .build())
            .build();
        
        return ResponseEntity.badRequest().body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        
        ErrorResponse error = ErrorResponse.builder()
            .success(false)
            .error(ErrorDetails.builder()
                .code("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred")
                .timestamp(Instant.now())
                .build())
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```

---

## 9. Code Structure

### Project Package Structure

```
src/main/java/com/yourname/bookmyshow/
├── config/
│   ├── SecurityConfig.java
│   ├── RedisConfig.java
│   ├── KafkaConfig.java
│   ├── JpaConfig.java
│   └── SwaggerConfig.java
├── controller/
│   ├── AuthController.java
│   ├── UserController.java
│   ├── MovieController.java
│   ├── ShowController.java
│   ├── BookingController.java
│   └── PaymentController.java
├── service/
│   ├── AuthService.java
│   ├── UserService.java
│   ├── CatalogService.java
│   ├── BookingService.java
│   ├── SeatLockService.java
│   ├── PaymentService.java
│   └── NotificationService.java
├── repository/
│   ├── UserRepository.java
│   ├── MovieRepository.java
│   ├── TheaterRepository.java
│   ├── ShowRepository.java
│   ├── ShowSeatRepository.java
│   ├── BookingRepository.java
│   └── PaymentRepository.java
├── model/
│   ├── entity/
│   │   ├── User.java
│   │   ├── Movie.java
│   │   ├── Theater.java
│   │   ├── Screen.java
│   │   ├── Seat.java
│   │   ├── Show.java
│   │   ├── ShowSeat.java
│   │   ├── Booking.java
│   │   ├── BookingSeat.java
│   │   └── Payment.java
│   ├── dto/
│   │   ├── request/
│   │   │   ├── BookingInitiateRequest.java
│   │   │   ├── BookingConfirmRequest.java
│   │   │   └── PaymentRequest.java
│   │   └── response/
│   │       ├── BookingInitiateResponse.java
│   │       ├── BookingConfirmResponse.java
│   │       └── ShowSeatsResponse.java
│   └── enums/
│       ├── BookingStatus.java
│       ├── PaymentStatus.java
│       ├── ShowSeatStatus.java
│       └── SeatType.java
├── exception/
│   ├── BookingException.java
│   ├── SeatsNotAvailableException.java
│   ├── BookingExpiredException.java
│   ├── PaymentFailedException.java
│   └── GlobalExceptionHandler.java
├── event/
│   ├── BookingEvent.java
│   ├── BookingInitiatedEvent.java
│   ├── BookingConfirmedEvent.java
│   ├── BookingCancelledEvent.java
│   ├── BookingEventPublisher.java
│   └── BookingEventListener.java
├── payment/
│   ├── PaymentGateway.java
│   ├── StripePaymentGateway.java
│   ├── RazorpayPaymentGateway.java
│   └── PaymentGatewayFactory.java
├── scheduler/
│   └── BookingExpiryScheduler.java
├── security/
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java
│   └── UserDetailsServiceImpl.java
└── util/
    ├── BookingReferenceGenerator.java
    └── DateTimeUtil.java
```

---

## 📚 Key Takeaways

After implementing this LLD, you'll have hands-on experience with:

1. **Distributed Locking** with Redis
2. **Database Transactions** with proper isolation levels
3. **State Machines** for complex workflows
4. **Design Patterns** in real-world scenarios
5. **Event-Driven Architecture** with Kafka
6. **Error Handling** strategies
7. **API Design** best practices
8. **Concurrency Control** mechanisms

---

## 🎯 Implementation Order

1. **Phase 1**: Domain models + Database schema
2. **Phase 2**: Repository layer + Basic CRUD
3. **Phase 3**: Catalog service (read operations)
4. **Phase 4**: Seat locking service (Redis integration)
5. **Phase 5**: Booking service (core logic)
6. **Phase 6**: Payment service integration
7. **Phase 7**: Background jobs (expiry scheduler)
8. **Phase 8**: Event publishing & listeners
9. **Phase 9**: Testing (unit + integration)
10. **Phase 10**: Deployment & monitoring

---

**Next**: Check `IMPLEMENTATION_GUIDE.md` for step-by-step coding instructions!
