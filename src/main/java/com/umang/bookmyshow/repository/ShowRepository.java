package com.umang.bookmyshow.repository;

import com.umang.bookmyshow.model.entity.Show;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShowRepository extends JpaRepository<Show, Long> {

    @Query("SELECT s FROM Show s WHERE s.movie.id = :movieId "
            + "AND s.screen.theater.city.id = :cityId AND s.showDate = :date "
            + "AND s.status = com.umang.bookmyshow.model.enums.ShowStatus.ACTIVE")
    List<Show> findShows(@Param("movieId") Long movieId,
                         @Param("cityId") Long cityId,
                         @Param("date") LocalDate date);

    /**
     * Atomically decrement the denormalized available-seat counter. Doing this as a
     * single UPDATE (rather than read-modify-write in Java) avoids a lost-update race.
     */
    @Modifying
    @Query("UPDATE Show s SET s.availableSeats = s.availableSeats - :count WHERE s.id = :showId")
    int decrementAvailableSeats(@Param("showId") Long showId, @Param("count") int count);

    @Modifying
    @Query("UPDATE Show s SET s.availableSeats = s.availableSeats + :count WHERE s.id = :showId")
    int incrementAvailableSeats(@Param("showId") Long showId, @Param("count") int count);
}
