# BookMyShow - High Level Design (HLD)

## 📋 Table of Contents
1. [Problem Statement](#problem-statement)
2. [Functional Requirements](#functional-requirements)
3. [Non-Functional Requirements](#non-functional-requirements)
4. [Back-of-Envelope Calculations](#back-of-envelope-calculations)
5. [System APIs](#system-apis)
6. [High-Level Architecture](#high-level-architecture)
7. [Database Design](#database-design)
8. [Core Algorithms & Flows](#core-algorithms--flows)
9. [Scalability Considerations](#scalability-considerations)
10. [Technology Stack](#technology-stack)

---

## 1. Problem Statement

Design a backend system for an online ticket booking platform (like BookMyShow) that allows users to:
- Browse movies, events, and shows
- Search for theaters and showtimes
- Book tickets with seat selection
- Make payments
- Manage bookings (view, cancel)

### Real-World Challenges
- **Concurrency**: Multiple users trying to book the same seats
- **Consistency**: Prevent double-booking under any circumstances
- **High Traffic**: Handle peak loads (new movie releases, popular events)
- **Complex Transactions**: Atomic booking process (select → lock → pay → confirm)

---

## 2. Functional Requirements

### Core Features (Must Have)
1. **User Management**
   - User registration and authentication
   - User profile management
   - Booking history

2. **Catalog Management**
   - Movies with details (title, description, genre, duration, rating)
   - Events (concerts, sports, plays)
   - Theaters with multiple screens (halls)
   - Shows with specific date, time, and pricing

3. **Search & Discovery**
   - Search movies by name, genre, language
   - Filter by city, theater, date, time
   - Show available seats for a show

4. **Booking System**
   - Select show and seats
   - Hold seats temporarily (10 minutes)
   - Process payment
   - Generate booking confirmation

5. **Payment Processing**
   - Multiple payment methods
   - Payment gateway integration
   - Refund processing

6. **Booking Management**
   - View booking details
   - Cancel bookings (with rules)
   - Download/email tickets

### Nice to Have
- Recommendations based on history
- Reviews and ratings
- Loyalty programs
- Dynamic pricing
- Notifications (SMS, Email, Push)

---

## 3. Non-Functional Requirements

### Performance
- **Latency**: 
  - Search queries: < 200ms (p95)
  - Booking API: < 500ms (p95)
  - Seat availability: < 100ms (p95)
- **Throughput**: Handle 10,000 concurrent users

### Availability
- **Uptime**: 99.9% (8.76 hours downtime/year acceptable)
- **Fault Tolerance**: System should gracefully degrade

### Consistency
- **Strong Consistency** for booking operations (no double-booking)
- **Eventual Consistency** acceptable for search/catalog

### Scalability
- Horizontal scaling for stateless services
- Database sharding if needed
- Handle 10x traffic during peak times

### Security
- PCI-DSS compliance for payment data
- JWT-based authentication
- API rate limiting
- Input validation and SQL injection prevention

---

## 4. Back-of-Envelope Calculations

### Assumptions
- **Active Users**: 10 million monthly active users (MAU)
- **Daily Active Users (DAU)**: 1 million (10% of MAU)
- **Peak Traffic**: 5x average (major movie releases)
- **Average Bookings**: 100,000 bookings/day
- **Average Tickets per Booking**: 2.5 tickets
- **Shows per Day**: 50,000 shows across all theaters

### Traffic Estimates
- **Queries Per Second (QPS)**:
  - Read operations (search, browse): 1M DAU × 20 queries/day ÷ 86,400s ≈ **230 QPS** (avg)
  - Peak: 230 × 5 = **1,150 QPS**
  
- **Booking Transactions**:
  - 100K bookings/day ÷ 86,400s ≈ **1.2 TPS** (avg)
  - Peak: 1.2 × 10 = **12 TPS** (peak, Friday evenings)

### Storage Estimates
- **Users**: 10M users × 1KB = **10 GB**
- **Movies/Events**: 10,000 active × 10KB = **100 MB**
- **Theaters**: 5,000 theaters × 5KB = **25 MB**
- **Shows**: 50K shows/day × 365 days × 1KB = **18 GB/year**
- **Bookings**: 100K bookings/day × 365 days × 2KB = **73 GB/year**
- **Total (5 years)**: ~400 GB (easily fits in single DB, but we'll design for scale)

### Bandwidth
- **Incoming**: 1,150 QPS × 1KB = **1.15 MB/s** (peak)
- **Outgoing**: 1,150 QPS × 10KB = **11.5 MB/s** (peak)

---

## 5. System APIs

### Authentication APIs
```
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh-token
POST /api/v1/auth/logout
```

### User APIs
```
GET    /api/v1/users/{userId}
PUT    /api/v1/users/{userId}
GET    /api/v1/users/{userId}/bookings
```

### Movie/Event APIs
```
GET    /api/v1/movies?city={city}&genre={genre}&language={lang}
GET    /api/v1/movies/{movieId}
GET    /api/v1/events?city={city}&category={category}
GET    /api/v1/events/{eventId}
```

### Theater & Show APIs
```
GET    /api/v1/cities
GET    /api/v1/cities/{cityId}/theaters
GET    /api/v1/theaters/{theaterId}
GET    /api/v1/movies/{movieId}/shows?city={city}&date={date}&theaterId={theaterId}
GET    /api/v1/shows/{showId}
GET    /api/v1/shows/{showId}/seats
```

### Booking APIs (Core - Most Important)
```
POST   /api/v1/bookings/initiate
       Body: { showId, seatIds[], userId }
       Returns: { bookingId, expiryTime, amount }

PUT    /api/v1/bookings/{bookingId}/confirm
       Body: { paymentId, paymentMethod }
       Returns: { bookingConfirmation }

DELETE /api/v1/bookings/{bookingId}
       (Cancel booking)

GET    /api/v1/bookings/{bookingId}
```

### Payment APIs
```
POST   /api/v1/payments/process
       Body: { bookingId, amount, paymentMethod, paymentDetails }
       Returns: { paymentId, status }

GET    /api/v1/payments/{paymentId}/status
```

---

## 6. High-Level Architecture

### Architecture Diagram

```
                                    ┌─────────────────┐
                                    │   API Gateway   │
                                    │  (Rate Limiting)│
                                    └────────┬────────┘
                                             │
                        ┌────────────────────┼────────────────────┐
                        │                    │                    │
                        ▼                    ▼                    ▼
              ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
              │  User Service    │  │ Catalog Service  │  │ Booking Service  │
              │  - Auth          │  │ - Movies/Events  │  │ - Seat Locking   │
              │  - Profile       │  │ - Theaters       │  │ - Transactions   │
              │  - JWT           │  │ - Shows          │  │ - State Machine  │
              └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘
                       │                     │                      │
                       │                     │                      │
              ┌────────▼─────────────────────▼──────────────────────▼─────────┐
              │                                                                │
              │                         Message Queue                         │
              │                         (Apache Kafka)                        │
              │                                                                │
              └───┬──────────────┬──────────────┬──────────────┬──────────────┘
                  │              │              │              │
                  ▼              ▼              ▼              ▼
        ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
        │  Payment    │  │ Notification│  │  Analytics  │  │   Audit     │
        │  Service    │  │  Service    │  │  Service    │  │  Service    │
        └──────┬──────┘  └─────────────┘  └─────────────┘  └─────────────┘
               │
               ▼
        ┌─────────────────┐
        │ Payment Gateway │
        │  (Stripe/Razor) │
        └─────────────────┘


                            ┌─────── Data Layer ───────┐

        ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
        │ PostgreSQL  │    │    Redis    │    │   MongoDB   │    │Elasticsearch│
        │ (Primary DB)│    │   (Cache)   │    │  (Logs)     │    │  (Search)   │
        │ - Users     │    │ - Sessions  │    │             │    │  - Movies   │
        │ - Bookings  │    │ - Seat Lock │    │             │    │  - Theaters │
        │ - Shows     │    │             │    │             │    │             │
        └─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
```

### Components Description

#### 1. **API Gateway**
- Entry point for all client requests
- Responsibilities:
  - Request routing
  - Authentication/Authorization
  - Rate limiting (prevent abuse)
  - Request/Response transformation
  - SSL termination
- Technology: Spring Cloud Gateway / Kong / AWS API Gateway

#### 2. **User Service**
- User registration and authentication
- JWT token generation and validation
- User profile management
- Booking history retrieval
- Database: PostgreSQL (users table)

#### 3. **Catalog Service**
- Manages movies, events, theaters, shows
- Read-heavy service (95% reads)
- Heavy caching strategy
- Search functionality integration
- Database: PostgreSQL + Elasticsearch (for search)

#### 4. **Booking Service** (Most Critical)
- Core business logic for ticket booking
- Seat selection and locking mechanism
- Transaction management (ACID compliance)
- Temporary holds with TTL
- Integration with payment service
- Database: PostgreSQL + Redis (for locks)

#### 5. **Payment Service**
- Payment gateway integration
- Handle multiple payment methods
- Idempotency for payment requests
- Webhook handling for async payment confirmations
- Refund processing
- PCI-DSS compliance considerations

#### 6. **Notification Service**
- Email confirmations
- SMS notifications
- Push notifications (mobile apps)
- Async processing via message queue

#### 7. **Analytics Service**
- Track user behavior
- Popular movies/shows
- Revenue analytics
- Real-time dashboards

#### 8. **Message Queue (Kafka)**
- Decouple services
- Async processing
- Event sourcing for audit
- Topics:
  - `booking.created`
  - `booking.confirmed`
  - `booking.cancelled`
  - `payment.completed`
  - `notification.send`

---

## 7. Database Design

### PostgreSQL Schema (Relational)

#### Users Table
```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
```

#### Cities Table
```sql
CREATE TABLE cities (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    state VARCHAR(100),
    country VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### Theaters Table
```sql
CREATE TABLE theaters (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    city_id INT REFERENCES cities(id),
    address TEXT,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    total_screens INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_theaters_city ON theaters(city_id);
```

#### Screens (Halls) Table
```sql
CREATE TABLE screens (
    id BIGSERIAL PRIMARY KEY,
    theater_id BIGINT REFERENCES theaters(id),
    name VARCHAR(100), -- Screen 1, IMAX, etc.
    total_seats INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### Seats Table
```sql
CREATE TABLE seats (
    id BIGSERIAL PRIMARY KEY,
    screen_id BIGINT REFERENCES screens(id),
    row_num VARCHAR(5), -- A, B, C, etc.
    seat_number INT,
    seat_type VARCHAR(50), -- REGULAR, PREMIUM, VIP
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(screen_id, row_num, seat_number)
);

CREATE INDEX idx_seats_screen ON seats(screen_id);
```

#### Movies Table
```sql
CREATE TABLE movies (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    duration_minutes INT,
    genre VARCHAR(100),
    language VARCHAR(50),
    release_date DATE,
    rating VARCHAR(10), -- PG, PG-13, R, etc.
    poster_url VARCHAR(500),
    trailer_url VARCHAR(500),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_movies_genre ON movies(genre);
CREATE INDEX idx_movies_language ON movies(language);
CREATE INDEX idx_movies_active ON movies(is_active);
```

#### Shows Table (Critical)
```sql
CREATE TABLE shows (
    id BIGSERIAL PRIMARY KEY,
    movie_id BIGINT REFERENCES movies(id),
    screen_id BIGINT REFERENCES screens(id),
    show_date DATE NOT NULL,
    show_time TIME NOT NULL,
    end_time TIME, -- Calculated: show_time + movie_duration
    base_price DECIMAL(10, 2),
    available_seats INT, -- Denormalized for quick check
    total_seats INT,
    status VARCHAR(50) DEFAULT 'ACTIVE', -- ACTIVE, CANCELLED, HOUSEFULL
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shows_movie ON shows(movie_id);
CREATE INDEX idx_shows_screen ON shows(screen_id);
CREATE INDEX idx_shows_date ON shows(show_date);
CREATE INDEX idx_shows_date_movie ON shows(show_date, movie_id);
```

#### Show_Seats Table (Junction with Pricing)
```sql
CREATE TABLE show_seats (
    id BIGSERIAL PRIMARY KEY,
    show_id BIGINT REFERENCES shows(id),
    seat_id BIGINT REFERENCES seats(id),
    price DECIMAL(10, 2), -- Can vary from base_price
    status VARCHAR(50) DEFAULT 'AVAILABLE', -- AVAILABLE, LOCKED, BOOKED
    locked_at TIMESTAMP,
    locked_by BIGINT REFERENCES users(id),
    booking_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(show_id, seat_id)
);

CREATE INDEX idx_show_seats_show ON show_seats(show_id);
CREATE INDEX idx_show_seats_status ON show_seats(status);
CREATE INDEX idx_show_seats_locked ON show_seats(locked_at) WHERE status = 'LOCKED';
```

#### Bookings Table (Critical)
```sql
CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    show_id BIGINT REFERENCES shows(id),
    booking_reference VARCHAR(50) UNIQUE NOT NULL,
    total_amount DECIMAL(10, 2),
    booking_status VARCHAR(50), -- INITIATED, CONFIRMED, CANCELLED, EXPIRED
    payment_status VARCHAR(50), -- PENDING, COMPLETED, FAILED, REFUNDED
    payment_id VARCHAR(255),
    payment_method VARCHAR(50),
    booked_at TIMESTAMP,
    expires_at TIMESTAMP, -- 10 minutes from creation
    confirmed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bookings_user ON bookings(user_id);
CREATE INDEX idx_bookings_show ON bookings(show_id);
CREATE INDEX idx_bookings_status ON bookings(booking_status);
CREATE INDEX idx_bookings_reference ON bookings(booking_reference);
CREATE INDEX idx_bookings_expires ON bookings(expires_at) WHERE booking_status = 'INITIATED';
```

#### Booking_Seats Table
```sql
CREATE TABLE booking_seats (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT REFERENCES bookings(id),
    show_seat_id BIGINT REFERENCES show_seats(id),
    price DECIMAL(10, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_booking_seats_booking ON booking_seats(booking_id);
```

#### Payments Table
```sql
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT REFERENCES bookings(id),
    amount DECIMAL(10, 2),
    payment_method VARCHAR(50),
    payment_gateway VARCHAR(50),
    gateway_transaction_id VARCHAR(255),
    status VARCHAR(50), -- INITIATED, SUCCESS, FAILED
    payment_details JSONB, -- Store additional gateway-specific info
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payments_booking ON payments(booking_id);
```

### Redis Data Structures (Caching & Locking)

#### 1. Seat Lock (Temporary Hold)
```
Key: seat:lock:{show_id}:{seat_id}
Value: {user_id, booking_id, timestamp}
TTL: 600 seconds (10 minutes)
```

#### 2. Show Available Seats Cache
```
Key: show:seats:{show_id}
Value: JSON of available seats
TTL: 60 seconds (short-lived, high consistency)
```

#### 3. User Session
```
Key: session:{user_id}
Value: JWT refresh token, user data
TTL: 7 days
```

#### 4. Rate Limiting
```
Key: ratelimit:{user_id}:{endpoint}
Value: request count
TTL: 60 seconds (sliding window)
```

### Elasticsearch Schema (Search & Discovery)

#### Movies Index
```json
{
  "mappings": {
    "properties": {
      "id": { "type": "long" },
      "title": { "type": "text", "analyzer": "standard" },
      "description": { "type": "text" },
      "genre": { "type": "keyword" },
      "language": { "type": "keyword" },
      "rating": { "type": "keyword" },
      "release_date": { "type": "date" },
      "is_active": { "type": "boolean" }
    }
  }
}
```

#### Theaters Index
```json
{
  "mappings": {
    "properties": {
      "id": { "type": "long" },
      "name": { "type": "text" },
      "city": { "type": "keyword" },
      "location": { "type": "geo_point" },
      "total_screens": { "type": "integer" }
    }
  }
}
```

---

## 8. Core Algorithms & Flows

### 8.1 Booking Flow (Most Critical)

```
1. User initiates booking:
   ├─ Select show
   ├─ View available seats
   ├─ Select seats
   └─ Click "Book"

2. Backend: Initiate Booking
   ├─ START TRANSACTION
   ├─ Check seat availability in DB
   ├─ Acquire distributed lock on seats (Redis)
   │  └─ If lock fails → Return "Seats already selected"
   ├─ Update show_seats status to 'LOCKED'
   ├─ Update show.available_seats (decrement)
   ├─ Create booking record (status: INITIATED)
   ├─ Set expiry time (current_time + 10 minutes)
   ├─ COMMIT TRANSACTION
   └─ Return booking_id and amount to user

3. User makes payment:
   ├─ Frontend redirects to payment gateway
   └─ User enters payment details

4. Payment Processing:
   ├─ Payment service calls gateway API
   ├─ Wait for response (or webhook)
   └─ If success → Confirm booking
      If failure → Release seats

5. Confirm Booking (on payment success):
   ├─ START TRANSACTION
   ├─ Update booking status to 'CONFIRMED'
   ├─ Update show_seats status to 'BOOKED'
   ├─ Update booking.payment_id
   ├─ COMMIT TRANSACTION
   ├─ Publish event: booking.confirmed
   └─ Return confirmation to user

6. Background Job: Expire Old Bookings
   ├─ Every minute, scan bookings where:
   │  └─ status = 'INITIATED' AND expires_at < NOW()
   ├─ For each expired booking:
   │  ├─ START TRANSACTION
   │  ├─ Update booking status to 'EXPIRED'
   │  ├─ Update show_seats status to 'AVAILABLE'
   │  ├─ Update show.available_seats (increment)
   │  ├─ Release Redis lock
   │  └─ COMMIT TRANSACTION
```

### 8.2 Seat Locking Strategy (Pessimistic Locking)

**Problem**: Multiple users clicking same seats simultaneously

**Solution**: Distributed locking with Redis + Database transactions

```java
// Pseudo-code
public BookingResponse initiateBooking(Long showId, List<Long> seatIds, Long userId) {
    // 1. Try to acquire locks in Redis
    for (Long seatId : seatIds) {
        String lockKey = "seat:lock:" + showId + ":" + seatId;
        boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, userId, Duration.ofMinutes(10));
        
        if (!acquired) {
            // Rollback all locks acquired so far
            releaseLocks(acquiredLocks);
            throw new SeatNotAvailableException("Seat already selected by another user");
        }
        acquiredLocks.add(lockKey);
    }
    
    // 2. Start database transaction
    @Transactional
    {
        // 3. Double-check seat availability in DB (defense in depth)
        List<ShowSeat> showSeats = showSeatRepository
            .findByShowIdAndSeatIdInAndStatus(showId, seatIds, "AVAILABLE");
        
        if (showSeats.size() != seatIds.size()) {
            throw new SeatNotAvailableException("Some seats are no longer available");
        }
        
        // 4. Update seats to LOCKED
        showSeats.forEach(seat -> {
            seat.setStatus("LOCKED");
            seat.setLockedAt(Instant.now());
            seat.setLockedBy(userId);
        });
        showSeatRepository.saveAll(showSeats);
        
        // 5. Create booking record
        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setShowId(showId);
        booking.setBookingReference(generateBookingReference());
        booking.setStatus("INITIATED");
        booking.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        // ... set other fields
        bookingRepository.save(booking);
        
        // 6. Update show available seats count
        showRepository.decrementAvailableSeats(showId, seatIds.size());
        
        return new BookingResponse(booking.getId(), booking.getTotalAmount(), 
                                    booking.getExpiresAt());
    }
}
```

### 8.3 Handling Race Conditions

**Scenario**: Two users click "Book" for same seat at exact same time

**Layers of Defense**:

1. **Redis Distributed Lock** (First line of defense)
   - Atomic `SET NX` operation
   - Only one user gets the lock
   - TTL ensures auto-cleanup

2. **Database Transaction with Row Locking** (Second line)
   ```sql
   SELECT * FROM show_seats 
   WHERE show_id = ? AND seat_id IN (?) AND status = 'AVAILABLE'
   FOR UPDATE; -- Row-level lock
   ```

3. **Database Constraints** (Last line)
   - Unique constraint on (show_id, seat_id)
   - Check constraint on status transitions

### 8.4 Background Job: Expire Stale Bookings

```java
@Scheduled(fixedRate = 60000) // Every minute
public void expireStaleBookings() {
    List<Booking> expiredBookings = bookingRepository
        .findByStatusAndExpiresAtBefore("INITIATED", Instant.now());
    
    for (Booking booking : expiredBookings) {
        try {
            @Transactional
            {
                // Update booking
                booking.setStatus("EXPIRED");
                bookingRepository.save(booking);
                
                // Release seats
                List<ShowSeat> seats = showSeatRepository
                    .findByBookingIdAndStatus(booking.getId(), "LOCKED");
                seats.forEach(seat -> {
                    seat.setStatus("AVAILABLE");
                    seat.setLockedAt(null);
                    seat.setLockedBy(null);
                });
                showSeatRepository.saveAll(seats);
                
                // Update show count
                showRepository.incrementAvailableSeats(booking.getShowId(), seats.size());
                
                // Release Redis locks
                seats.forEach(seat -> {
                    String lockKey = "seat:lock:" + booking.getShowId() + ":" + seat.getSeatId();
                    redisTemplate.delete(lockKey);
                });
                
                // Publish event
                eventPublisher.publish(new BookingExpiredEvent(booking.getId()));
            }
        } catch (Exception e) {
            log.error("Error expiring booking: " + booking.getId(), e);
        }
    }
}
```

---

## 9. Scalability Considerations

### 9.1 Read Scaling (Catalog Service)

**Problem**: 95% of traffic is reads (searching, browsing)

**Solutions**:

1. **Multi-layer Caching**
   ```
   Client → CDN (static content) 
         → Application Cache (Redis) 
         → Database Read Replicas 
         → Primary Database
   ```

2. **Cache Strategy**:
   - **Movies list**: Cache for 1 hour
   - **Show timings**: Cache for 5 minutes
   - **Seat availability**: Cache for 30 seconds (with cache invalidation)

3. **Database Read Replicas**:
   - Route all read queries to replicas
   - Primary handles only writes

4. **Elasticsearch for Search**:
   - Offload search queries from PostgreSQL
   - Full-text search with filters
   - Faceted search (genre, language, theater)

### 9.2 Write Scaling (Booking Service)

**Problem**: High contention on popular shows

**Solutions**:

1. **Database Connection Pooling**:
   - HikariCP with optimal pool size
   - Separate pool for booking transactions

2. **Optimistic Locking for Low Contention**:
   - Use version column
   - Retry on conflict

3. **Pessimistic Locking for High Contention**:
   - SELECT FOR UPDATE
   - Keep transaction duration minimal

4. **Queue-based Processing**:
   - For extremely popular shows (e.g., Avengers opening day)
   - Put booking requests in queue
   - Process serially with fairness

### 9.3 Database Sharding (Future)

**When**: If single database becomes bottleneck (> 10M bookings/month)

**Sharding Strategy**:
- **Shard by City**: Each city's data in separate shard
  - Pros: Most queries are city-specific
  - Cons: Uneven load distribution
  
- **Shard by Show Date**: Historical vs current data
  - Pros: Archive old data easily
  - Cons: Complex queries across time ranges

### 9.4 Microservices Scaling

Each service scales independently:

```
API Gateway (2-3 instances)
    ├─ User Service (2-3 instances)
    ├─ Catalog Service (5-10 instances) ← Read-heavy
    ├─ Booking Service (3-5 instances) ← Write-heavy, stateless
    ├─ Payment Service (2-3 instances)
    └─ Notification Service (2-3 instances)
```

### 9.5 Handling Peak Load

**Scenario**: New Marvel movie release at midnight

**Strategies**:

1. **Auto-scaling**: Configure K8s HPA (Horizontal Pod Autoscaler)
   ```yaml
   minReplicas: 3
   maxReplicas: 20
   targetCPUUtilizationPercentage: 70
   ```

2. **Rate Limiting**: Per user, per IP
   ```
   Authenticated users: 10 req/sec
   Anonymous users: 2 req/sec
   ```

3. **Circuit Breaker**: Prevent cascade failures
   - If payment service down → Graceful degradation
   - Show message: "Payment service temporarily unavailable"

4. **Queue-based Booking**:
   - Virtual waiting room for extremely high demand
   - Process bookings in order with fairness

---

## 10. Technology Stack

### Backend
- **Framework**: Spring Boot 3.2
- **Language**: Java 17
- **Build Tool**: Maven
- **API Style**: RESTful

### Databases
- **Primary DB**: PostgreSQL 15
- **Cache**: Redis 7
- **Search**: Elasticsearch 8
- **Message Queue**: Apache Kafka 3.5

### Infrastructure
- **Containerization**: Docker
- **Orchestration**: Kubernetes
- **Cloud**: AWS (EC2, RDS, ElastiCache, SQS)
- **API Gateway**: Spring Cloud Gateway / Kong

### Observability
- **Metrics**: Micrometer + Prometheus
- **Logging**: Logback + ELK Stack
- **Tracing**: Spring Cloud Sleuth + Zipkin
- **APM**: (Optional) New Relic / Datadog

### Security
- **Authentication**: JWT (JSON Web Tokens)
- **Authorization**: Spring Security with role-based access
- **API Security**: Rate limiting, input validation
- **Payment Security**: PCI-DSS compliance considerations

### Testing
- **Unit Tests**: JUnit 5 + Mockito
- **Integration Tests**: Testcontainers
- **Load Testing**: JMeter / Gatling
- **API Testing**: Postman / REST Assured

### CI/CD
- **Version Control**: Git + GitHub
- **CI/CD**: GitHub Actions
- **Container Registry**: Docker Hub / AWS ECR

---

## 📚 Learning Outcomes

After implementing this system, you will understand:

1. **Concurrency Control**: Pessimistic vs optimistic locking
2. **Distributed Locking**: Using Redis for coordination
3. **Transaction Management**: ACID properties in practice
4. **Caching Strategies**: Multi-layer caching, cache invalidation
5. **Database Design**: Normalization, indexing, query optimization
6. **Event-Driven Architecture**: Kafka for async processing
7. **API Design**: RESTful best practices
8. **Scalability Patterns**: Horizontal scaling, read replicas, sharding
9. **High Availability**: Fault tolerance, graceful degradation
10. **Microservices**: Service decomposition, inter-service communication

---