# BookMyShow — Diagrams

Mermaid diagrams (render natively on GitHub). Generated from the actual entities under
`src/main/java/com/umang/bookmyshow/model/entity/` and the service layer.

- [1. High-Level Design (HLD)](#1-high-level-design-hld)
- [2. UML Class Diagram](#2-uml-class-diagram)
- [3. Entity-Relationship Diagram](#3-entity-relationship-diagram)
- [4. Booking State Machine](#4-booking-state-machine)

---

## 1. High-Level Design (HLD)

```mermaid
flowchart TB
    subgraph Client
      Dash[React Admin Dashboard]
      API_Client[API clients / Swagger]
    end

    Dash & API_Client -->|HTTPS + JWT| LB[Load Balancer / Ingress]
    LB --> App[BookMyShow App<br/>Spring Boot · stateless · HPA-scaled]

    subgraph App_Internal[Application layer]
      Ctrl[Controllers<br/>Auth · Movie · Show · Booking] --> SecFilter[JWT filter chain]
      Ctrl --> Catalog[CatalogService<br/>cached reads]
      Ctrl --> Booking[BookingService<br/>state machine + locking]
      Booking --> Payment[PaymentService<br/>Strategy + idempotency]
      Booking --> Lock[SeatLockService]
      Sched[BookingExpiryScheduler] --> Booking
    end

    Lock -->|SETNX + TTL| Redis[(Redis<br/>seat locks)]
    Catalog -->|"@Cacheable"| Redis
    Booking -->|"FOR UPDATE / @Version"| PG[(PostgreSQL<br/>Flyway-managed)]
    Catalog --> PG
    Payment --> PG

    Booking -->|write event in same tx| Outbox[(outbox_events)]
    Poller["OutboxPoller @Scheduled"] -->|relay| Kafka{{Kafka<br/>booking-events}}
    Outbox --> Poller
    Payment -->|Resilience4j CB+Retry| GW[Payment gateway]
    Kafka --> Notif[Notification consumer]
    Kafka -. on failure .-> DLT{{booking-events.DLT}}

    App -.metrics.-> Prom[(Prometheus)]
    App -.traces.-> Tempo[(Tempo)]
    App -.JSON logs.-> Logs[(Log aggregator)]
    Prom & Tempo --> Graf[Grafana]
```

---

## 2. UML Class Diagram

```mermaid
classDiagram
    class User {
      +Long id
      +String email
      +String passwordHash
      +String firstName
      +String lastName
      +String phone
    }
    class City {
      +Long id
      +String name
      +String state
    }
    class Theater {
      +Long id
      +String name
      +String address
      +BigDecimal latitude
      +BigDecimal longitude
      +Integer totalScreens
    }
    class Screen {
      +Long id
      +String name
      +Integer totalSeats
    }
    class Seat {
      +Long id
      +String rowNum
      +Integer seatNumber
      +SeatType seatType
    }
    class Movie {
      +Long id
      +String title
      +String genre
      +String language
      +Integer durationMinutes
      +LocalDate releaseDate
      +Boolean isActive
    }
    class Show {
      +Long id
      +LocalDate showDate
      +LocalTime showTime
      +BigDecimal basePrice
      +Integer availableSeats
      +ShowStatus status
    }
    class ShowSeat {
      +Long id
      +BigDecimal price
      +ShowSeatStatus status
      +Instant lockedAt
      +Long version
    }
    class Booking {
      +Long id
      +String bookingReference
      +BigDecimal totalAmount
      +BookingStatus bookingStatus
      +PaymentStatus paymentStatus
      +Instant expiresAt
    }
    class BookingSeat {
      +Long id
      +BigDecimal price
    }
    class Payment {
      +Long id
      +BigDecimal amount
      +String paymentGateway
      +String gatewayTransactionId
      +PaymentStatus status
    }

    City "1" o-- "many" Theater
    Theater "1" *-- "many" Screen
    Screen "1" *-- "many" Seat
    Movie "1" o-- "many" Show
    Screen "1" o-- "many" Show
    Show "1" *-- "many" ShowSeat
    Seat "1" o-- "many" ShowSeat
    User "1" o-- "many" Booking
    Show "1" o-- "many" Booking
    Booking "1" *-- "many" BookingSeat
    ShowSeat "1" o-- "many" BookingSeat
    Booking "1" o-- "many" Payment
    User "1" ..> "many" ShowSeat : lockedBy
```

---

## 3. Entity-Relationship Diagram

```mermaid
erDiagram
    CITIES ||--o{ THEATERS : has
    THEATERS ||--o{ SCREENS : contains
    SCREENS ||--o{ SEATS : has
    MOVIES ||--o{ SHOWS : scheduled_as
    SCREENS ||--o{ SHOWS : hosts
    SHOWS ||--o{ SHOW_SEATS : has
    SEATS ||--o{ SHOW_SEATS : instance_of
    USERS ||--o{ BOOKINGS : makes
    SHOWS ||--o{ BOOKINGS : for
    BOOKINGS ||--o{ BOOKING_SEATS : includes
    SHOW_SEATS ||--o{ BOOKING_SEATS : reserved_as
    BOOKINGS ||--o{ PAYMENTS : paid_by
    USERS ||--o{ SHOW_SEATS : locks

    USERS {
      bigint id PK
      varchar email UK
      varchar password_hash
    }
    SHOWS {
      bigint id PK
      bigint movie_id FK
      bigint screen_id FK
      int available_seats
      varchar status
    }
    SHOW_SEATS {
      bigint id PK
      bigint show_id FK
      bigint seat_id FK
      varchar status
      bigint version "optimistic lock"
      bigint locked_by FK
    }
    BOOKINGS {
      bigint id PK
      bigint user_id FK
      bigint show_id FK
      varchar booking_reference UK
      varchar booking_status
      timestamptz expires_at
    }
    PAYMENTS {
      bigint id PK
      bigint booking_id FK
      varchar gateway_transaction_id
      varchar status
    }
```

---

## 4. Booking State Machine

```mermaid
stateDiagram-v2
    [*] --> INITIATED : initiateBooking()<br/>(seats LOCKED, 10-min hold)
    INITIATED --> CONFIRMED : confirmBooking()<br/>payment success → seats BOOKED
    INITIATED --> EXPIRED : expiry job<br/>(hold elapsed) → seats freed
    INITIATED --> CANCELLED : cancelBooking()<br/>→ seats freed
    CONFIRMED --> CANCELLED : user cancels
    CONFIRMED --> REFUNDED : refund processed
    EXPIRED --> [*]
    CANCELLED --> [*]
    REFUNDED --> [*]
```
