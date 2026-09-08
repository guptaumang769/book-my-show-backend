package com.umang.bookmyshow.repository;

import com.umang.bookmyshow.model.entity.Movie;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    List<Movie> findByIsActiveTrue();

    List<Movie> findByGenreIgnoreCaseAndIsActiveTrue(String genre);

    @Query("SELECT DISTINCT m FROM Movie m JOIN Show s ON s.movie = m "
            + "JOIN s.screen sc JOIN sc.theater t "
            + "WHERE t.city.id = :cityId AND m.isActive = true")
    List<Movie> findActiveByCityId(@Param("cityId") Long cityId);

    @Query("SELECT DISTINCT m FROM Movie m JOIN Show s ON s.movie = m "
            + "JOIN s.screen sc JOIN sc.theater t "
            + "WHERE t.city.id = :cityId AND m.isActive = true "
            + "AND LOWER(m.genre) = LOWER(:genre)")
    List<Movie> findActiveByCityIdAndGenre(@Param("cityId") Long cityId,
                                           @Param("genre") String genre);
}
