package com.umang.bookmyshow.controller;

import com.umang.bookmyshow.dto.response.ApiResponse;
import com.umang.bookmyshow.dto.response.TheaterDTO;
import com.umang.bookmyshow.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/theaters")
@RequiredArgsConstructor
public class TheaterController {

    private final CatalogService catalogService;

    @Operation(summary = "List theaters in a city")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TheaterDTO>>> getTheaters(
            @RequestParam Long cityId) {
        return ResponseEntity.ok(ApiResponse.success(
                catalogService.getTheatersByCity(cityId)));
    }
}
