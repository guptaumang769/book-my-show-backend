package com.umang.bookmyshow.dto.request;

import com.umang.bookmyshow.exception.InvalidRequestException;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingInitiateRequest {

    @NotNull
    private Long userId;

    @NotNull
    private Long showId;

    /** ShowSeat ids the user wants to book. Max 10 per booking. */
    @NotEmpty
    @Size(max = 10, message = "Maximum 10 seats per booking")
    private List<Long> seatIds;

    /**
     * Programmatic guard mirroring the bean-validation constraints, used by callers that
     * build the request directly (e.g. tests) without going through the MVC validator.
     */
    public void validate() {
        if (userId == null || showId == null || seatIds == null || seatIds.isEmpty()) {
            throw new InvalidRequestException("Required fields missing");
        }
        if (seatIds.size() > 10) {
            throw new InvalidRequestException("Maximum 10 seats per booking");
        }
    }
}
