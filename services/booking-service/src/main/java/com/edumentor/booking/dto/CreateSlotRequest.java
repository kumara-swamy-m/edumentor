package com.edumentor.booking.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateSlotRequest(
        @NotNull(message = "Start time is required")
        @Future(message = "Start time must be in the future")
        Instant startTime,

        @NotNull(message = "End time is required")
        Instant endTime,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "1.00", message = "Price must be at least 1.00")
        @DecimalMax(value = "100000.00", message = "Price must be at most 100000.00")
        @Digits(integer = 6, fraction = 2, message = "Price allows at most 2 decimal places")
        BigDecimal price
) {
}