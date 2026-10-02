package com.edumentor.booking.dto;

import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(@NotNull(message = "Slot id is required") Long slotId) {
}