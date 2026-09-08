package com.umang.bookmyshow.repository;

import com.umang.bookmyshow.model.entity.ShowSeat;
import com.umang.bookmyshow.model.enums.ShowSeatStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    List<ShowSeat> findByShowIdAndStatus(Long showId, ShowSeatStatus status);

    List<ShowSeat> findByShowId(Long showId);

    List<ShowSeat> findByBookingId(Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ss FROM ShowSeat ss WHERE ss.show.id = :showId AND ss.id IN :showSeatIds")
    List<ShowSeat> findByShowIdAndSeatIdInForUpdate(@Param("showId") Long showId,
                                                    @Param("showSeatIds") List<Long> showSeatIds);
}
