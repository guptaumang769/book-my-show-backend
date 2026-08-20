package com.umang.bookmyshow.model.entity;

import com.umang.bookmyshow.model.enums.ShowSeatStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-show state of a seat. This is the row that actually gets locked/booked.
 * The {@code @Version} column gives us optimistic locking: two transactions that
 * read the same AVAILABLE seat and both try to LOCK it will collide, and the
 * loser gets an OptimisticLockException instead of a silent double-book.
 */
@Entity
@Table(name = "show_seats")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowSeat extends BaseEntity {

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

    @Column(name = "locked_at")
    private Instant lockedAt;

    @ManyToOne
    @JoinColumn(name = "locked_by")
    private User lockedBy;

    @Column(name = "booking_id")
    private Long bookingId;

    @Version
    private Long version;
}
