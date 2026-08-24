package com.umang.bookmyshow.controller;

import com.umang.bookmyshow.dto.response.ApiResponse;
import com.umang.bookmyshow.dto.response.ShowSeatsResponse;
import com.umang.bookmyshow.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shows")
@RequiredArgsConstructor
public class ShowController {

    private final CatalogService catalogService;

    @Operation(summary = "Get available seats for a show")
    @GetMapping("/{showId}/seats")
    public ResponseEntity<ApiResponse<ShowSeatsResponse>> getSeats(@PathVariable Long showId) {
        return ResponseEntity.ok(ApiResponse.success(catalogService.getAvailableSeats(showId)));
    }
}
