package com.umang.bookmyshow.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Error envelope matching LLD section 8.2: {@code {"success": false, "error": {...}}}.
 * Structurally the same shape as {@link ApiResponse} with only the error branch populated.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private boolean success;
    private ErrorDetails error;
}
