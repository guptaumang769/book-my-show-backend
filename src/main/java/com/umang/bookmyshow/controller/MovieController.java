package com.umang.bookmyshow.controller;

import com.umang.bookmyshow.dto.response.ApiResponse;
import com.umang.bookmyshow.dto.response.MovieDTO;
import com.umang.bookmyshow.dto.response.ShowDTO;
import com.umang.bookmyshow.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/movies")
@RequiredArgsConstructor
public class MovieController {

    private final CatalogService catalogService;

    @Operation(summary = "List active movies, optionally filtered by city/genre/language")
    @GetMapping
    public ResponseEntity<ApiResponse<List<MovieDTO>>> getMovies(
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String language) {
        return ResponseEntity.ok(ApiResponse.success(
                catalogService.getMoviesByCity(cityId, genre, language)));
    }

    @Operation(summary = "List shows for a movie in a city on a date")
    @GetMapping("/{movieId}/shows")
    public ResponseEntity<ApiResponse<List<ShowDTO>>> getShows(
            @PathVariable Long movieId,
            @RequestParam Long cityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(
                catalogService.getShowsForMovie(movieId, cityId, date)));
    }
}
