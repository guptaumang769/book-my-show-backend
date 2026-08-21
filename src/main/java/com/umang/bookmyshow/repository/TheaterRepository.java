package com.umang.bookmyshow.repository;

import com.umang.bookmyshow.model.entity.Theater;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TheaterRepository extends JpaRepository<Theater, Long> {

    List<Theater> findByCityId(Long cityId);
}
