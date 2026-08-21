package com.umang.bookmyshow.repository;

import com.umang.bookmyshow.model.entity.Movie;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    List<Movie> findByIsActiveTrue();

    List<Movie> findByGenreIgnoreCaseAndIsActiveTrue(String genre);
}
